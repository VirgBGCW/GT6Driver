package com.example.gt6driver.sync;

import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import okhttp3.*;
import okio.BufferedSink;   // <-- add this

public class AzureUploader {
    // e.g., "sv=2024-01-01&ss=b&srt=o&sp=rwl&se=2030-01-01T00:00:00Z&spr=https&sig=..."
    // Ideally inject via secure config / remote fetch.
    private final String sasQuery;
    private final OkHttpClient http = new OkHttpClient();

    public AzureUploader(String sasQuery) {
        this.sasQuery = sasQuery.startsWith("?") ? sasQuery : "?" + sasQuery;
    }

    /** PUTs a full object (<= 256MB) using Put Blob. */
    public void putBlob(String baseContainerUrl, String blobPath, String mime, long contentLength, InputStream data) throws IOException {
        HttpUrl url = HttpUrl.parse(baseContainerUrl + "/" + blobPath + sasQuery);
        if (url == null) throw new IOException("Invalid URL");

        RequestBody body = new InputStreamRequestBody(mime, contentLength, data);
        Request req = new Request.Builder()
                .url(url)
                .put(body)
                .header("x-ms-blob-type", "BlockBlob")
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Azure PUT failed " + resp.code() + ": " + resp.message());
            }
        }
    }

    /** RequestBody that streams from InputStream without loading to memory. */
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

        @Override public void writeTo(BufferedSink sink) throws IOException {
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
