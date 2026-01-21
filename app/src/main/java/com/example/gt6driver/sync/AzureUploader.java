package com.example.gt6driver.sync;

import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AzureUploader {
    private static final String TAG = "GT6-Worker";

    // SAS query (may or may not start with '?'; we normalize in ctor)
    private final String sasQuery;
    private final OkHttpClient http;

    // Tunables
    private static final int BLOCK_SIZE_BYTES = 32 * 1024 * 1024; // 32 MiB
    private static final int MAX_PUT_BLOB     = 256 * 1024 * 1024; // Azure single PUT limit
    private static final int RETRIES          = 3;

    public AzureUploader(String sasQuery) {
        // Normalize to always include leading '?', unless empty/null
        if (sasQuery == null || sasQuery.isEmpty()) {
            this.sasQuery = "";
        } else {
            this.sasQuery = sasQuery.startsWith("?") ? sasQuery : "?" + sasQuery;
        }

        this.http = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(20))
                .readTimeout(java.time.Duration.ofMinutes(5))
                .writeTimeout(java.time.Duration.ofMinutes(5))
                .retryOnConnectionFailure(true)
                .build();
    }

    /** AUTO: small files via single Put Blob; large files via Put Block/Put Block List. */
    public void putBlobAuto(String baseContainerUrl, String blobPath, String mime, long contentLength, InputStream data) throws IOException {
        if (contentLength >= 0 && contentLength <= MAX_PUT_BLOB) {
            putBlobSmall(baseContainerUrl, blobPath, mime, contentLength, data, /*metadata=*/null);
        } else {
            putBlobLarge(baseContainerUrl, blobPath, mime, contentLength, data, /*metadata=*/null);
        }
    }

    /**
     * AUTO w/ metadata:
     * - If metadata is null or missing keys, we fill safe defaults:
     *   - createdat: ISO-8601 UTC now
     *   - driver: "Upload Agent"
     *
     * NOTE: device name must be provided by the caller (Worker) if desired.
     */
    public void putBlobAuto(String baseContainerUrl,
                            String blobPath,
                            String mime,
                            long contentLength,
                            InputStream data,
                            Map<String, String> metadata) throws IOException {

        Map<String, String> meta = normalizeAndDefaultMetadata(metadata);

        if (contentLength >= 0 && contentLength <= MAX_PUT_BLOB) {
            putBlobSmall(baseContainerUrl, blobPath, mime, contentLength, data, meta);
        } else {
            putBlobLarge(baseContainerUrl, blobPath, mime, contentLength, data, meta);
        }
    }

    // ✅ Convenience overload to avoid any generic/signature mismatch surprises
    public void putBlobAuto(String baseContainerUrl,
                            String blobPath,
                            String mime,
                            long contentLength,
                            InputStream data,
                            HashMap<String, String> metadata) throws IOException {
        putBlobAuto(baseContainerUrl, blobPath, mime, contentLength, data, (Map<String, String>) metadata);
    }

    /** Single-call Put Blob (<=256MB). */
    private void putBlobSmall(String baseContainerUrl,
                              String blobPath,
                              String mime,
                              long contentLength,
                              InputStream data,
                              Map<String, String> metadata) throws IOException {

        HttpUrl url = buildObjectUrl(baseContainerUrl, blobPath);
        if (url == null) throw new IOException("Invalid URL");

        RequestBody body = new InputStreamRequestBody(mime, contentLength, data);

        Request.Builder rb = new Request.Builder()
                .url(url)
                .put(body)
                .header("x-ms-blob-type", "BlockBlob");

        // Apply x-ms-meta-* headers (safe even if metadata is empty)
        applyMetadataHeaders(rb, metadata);

        try (Response resp = execWithRetry(rb.build())) {
            if (!resp.isSuccessful()) {
                throw new IOException("Azure Put Blob failed " + resp.code() + ": " + resp.message());
            }
        }
    }

    /** Multi-part Put Block / Put Block List for arbitrarily large files. */
    private void putBlobLarge(String baseContainerUrl,
                              String blobPath,
                              String mime,
                              long contentLength,
                              InputStream in,
                              Map<String, String> metadata) throws IOException {

        // 1) Stream blocks
        List<String> blockIds = new ArrayList<>();
        byte[] buf = new byte[BLOCK_SIZE_BYTES];
        int seq = 0;
        long total = 0;

        while (true) {
            int read = readFully(in, buf);
            if (read <= 0) break;

            String blockId = encodeBlockId(seq++);
            putBlock(baseContainerUrl, blobPath, blockId, buf, read);
            blockIds.add(blockId);

            total += read;
            Log.i(TAG, "Block uploaded: " + read + " bytes (total=" + total + " mime=" + mime + ") to " + blobPath);
        }

        // 2) Commit block list (THIS is where metadata must be applied for large blobs)
        putBlockList(baseContainerUrl, blobPath, blockIds, mime, metadata);
    }

    /** PUT …/blob?comp=block&blockid=base64 */
    private void putBlock(String baseContainerUrl,
                          String blobPath,
                          String blockIdB64,
                          byte[] data,
                          int len) throws IOException {

        HttpUrl base = buildObjectUrl(baseContainerUrl, blobPath);
        if (base == null) throw new IOException("Invalid URL");

        HttpUrl url = base.newBuilder()
                .addQueryParameter("comp", "block")
                .addQueryParameter("blockid", blockIdB64)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/octet-stream"),
                data,
                0,
                len
        );

        Request req = new Request.Builder()
                .url(url)
                .put(body)
                .build();

        try (Response resp = execWithRetry(req)) {
            if (!resp.isSuccessful()) {
                throw new IOException("Put Block failed " + resp.code() + ": " + resp.message());
            }
        }
    }

    /** PUT …/blob?comp=blocklist with XML body listing blocks in order (metadata applied here). */
    private void putBlockList(String baseContainerUrl,
                              String blobPath,
                              List<String> blockIds,
                              String mime,
                              Map<String, String> metadata) throws IOException {

        HttpUrl base = buildObjectUrl(baseContainerUrl, blobPath);
        if (base == null) throw new IOException("Invalid URL");

        HttpUrl url = base.newBuilder()
                .addQueryParameter("comp", "blocklist")
                .build();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                .append("<BlockList>");
        for (String id : blockIds) {
            xml.append("<Latest>").append(id).append("</Latest>");
        }
        xml.append("</BlockList>");

        Request.Builder rb = new Request.Builder()
                .url(url)
                .put(RequestBody.create(xml.toString(), MediaType.parse("application/xml")))
                .header("x-ms-blob-content-type", (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime);

        // Apply x-ms-meta-* headers at commit time
        applyMetadataHeaders(rb, metadata);

        try (Response resp = execWithRetry(rb.build())) {
            if (!resp.isSuccessful()) {
                throw new IOException("Put Block List failed " + resp.code() + ": " + resp.message());
            }
        }
    }

    /** Helper: deterministic, lexicographically sorted block IDs (5-digit seq). */
    private String encodeBlockId(int seq) {
        String raw = String.format(Locale.US, "blk-%05d", seq);
        return Base64.encodeToString(raw.getBytes(), Base64.NO_WRAP);
    }

    /** Read up to buf.length; returns bytes read or -1 on EOF. */
    private int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r <= 0) return (off == 0) ? r : off;
            off += r;
        }
        return off;
    }

    /** Retry wrapper with helpful diagnostics. */
    private Response execWithRetry(Request req) throws IOException {
        IOException last = null;
        for (int i = 0; i < RETRIES; i++) {
            try {
                Response resp = http.newCall(req).execute();
                if (!resp.isSuccessful()) {
                    String peek = null;
                    try { peek = resp.body() != null ? resp.peekBody(1024).string() : null; } catch (Exception ignore) {}
                    Log.e(TAG, "HTTP " + resp.code() + " " + resp.message() + (peek != null ? (" body=" + peek) : ""));
                }
                if (resp.isSuccessful() || i == RETRIES - 1) return resp;
                resp.close();
            } catch (IOException e) {
                last = e;
                Log.w(TAG, "IOException try " + (i + 1) + "/" + RETRIES + ": " + e.getMessage());
                if (i == RETRIES - 1) throw e;
            }
            try { Thread.sleep(1000L * (i + 1)); } catch (InterruptedException ignored) {}
        }
        throw last != null ? last : new IOException("Unknown network error");
    }

    /** Build the full object URL using safe path encoding and appending SAS. */
    private HttpUrl buildObjectUrl(String baseContainerUrl, String blobPath) {
        if (baseContainerUrl == null) return null;

        // Normalize base (no trailing '/')
        String normalizedBase = baseContainerUrl.replaceAll("/+$", "");

        // Normalize blob path (no leading '/')
        String bp = (blobPath == null) ? "" : blobPath.replaceAll("^/+", "");

        HttpUrl base = HttpUrl.parse(normalizedBase);
        if (base == null) return null;

        HttpUrl.Builder b = base.newBuilder();
        // Append each path segment safely
        if (!bp.isEmpty()) {
            for (String seg : bp.split("/")) {
                if (!seg.isEmpty()) b.addPathSegment(seg);
            }
        }

        // Append SAS query (already normalized to start with '?' or be empty)
        String built = b.build().toString();
        String withSas = built + (sasQuery == null ? "" : sasQuery);

        return HttpUrl.parse(withSas);
    }

    // ---------------- Metadata helpers ----------------

    /**
     * Normalize metadata and fill safe defaults if missing:
     * - createdat: ISO-8601 UTC now
     * - driver: "Upload Agent"
     *
     * (device is NOT filled here because uploader has no Context; caller should provide it.)
     */
    private static Map<String, String> normalizeAndDefaultMetadata(Map<String, String> in) {
        Map<String, String> meta = new HashMap<>();
        if (in != null) meta.putAll(in);

        // createdat default
        if (isBlank(meta.get("createdat"))) {
            meta.put("createdat", utcNowIso());
        }

        // driver default
        if (isBlank(meta.get("driver"))) {
            meta.put("driver", "Upload Agent");
        }

        // Normalize aliases so you can pass either "device" or "tablet"
        if (isBlank(meta.get("device")) && !isBlank(meta.get("tablet"))) {
            meta.put("device", meta.get("tablet"));
        }

        return meta;
    }

    private static void applyMetadataHeaders(Request.Builder rb, Map<String, String> metadata) {
        if (rb == null || metadata == null || metadata.isEmpty()) return;

        for (Map.Entry<String, String> e : metadata.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();

            if (k == null) continue;
            k = k.trim().toLowerCase(Locale.US);
            if (k.isEmpty()) continue;

            // Azure metadata key rules: use safe token-ish keys
            k = k.replaceAll("[^a-z0-9\\-]", "-");
            if (k.isEmpty()) continue;

            if (v == null) v = "";
            v = v.trim();

            rb.header("x-ms-meta-" + k, v);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty() || "null".equalsIgnoreCase(s.trim());
    }

    private static String utcNowIso() {
        try {
            // Available with desugaring / API 26+; you already use java.time.Duration
            return java.time.Instant.now().toString();
        } catch (Throwable t) {
            // Fallback
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return sdf.format(new java.util.Date(System.currentTimeMillis()));
        }
    }

    /** Streaming body for small uploads. */
    static class InputStreamRequestBody extends RequestBody {
        private final String mime;
        private final long length;
        private final InputStream in;

        InputStreamRequestBody(String mime, long length, InputStream in) {
            this.mime = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
            this.length = length;
            this.in = in;
        }

        @Override public MediaType contentType() { return MediaType.parse(mime); }
        @Override public long contentLength() { return length >= 0 ? length : -1; }

        @Override public void writeTo(okio.BufferedSink sink) throws IOException {
            try (InputStream is = in) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = is.read(buf)) != -1) {
                    sink.write(buf, 0, r);
                }
            }
        }
    }
}




