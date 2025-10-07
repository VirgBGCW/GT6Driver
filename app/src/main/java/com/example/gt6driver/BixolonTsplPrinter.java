package com.example.gt6driver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

public class BixolonTsplPrinter {

    private static final String TAG = "BIX";

    // Generic Serial Port Profile UUID
    private static final UUID GENERIC_SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // ===== Make these PUBLIC so ActionActivity can reference them =====
    public static final int RECEIPT_WIDTH_DOTS = 576; // 80mm paper; 384 for 58mm
    public static final int RECEIPT_FEED_LINES = 3;

    // Try these RFCOMM ports first (only used by exhaustive connector)
    private static final int[] PREFERRED_CHANNELS = {8, 9, 12, 5, 7, 10, 11, 4, 6, 3, 1, 2};

    private static final int CONNECT_TIMEOUT_UUID_MS = 5000;
    private static final int CONNECT_TIMEOUT_CHAN_MS = 2500;
    private static final int REFLECTION_MAX_CHANNEL  = 30;

    private static final ExecutorService CONNECT_EXEC = Executors.newCachedThreadPool();

    private BluetoothSocket socket;
    private OutputStream out;
    private String connectedName = null;
    private String connectedAddr = null;

    /* =========================
       SIMPLE ESC/POS CONNECT (like your working code)
       ========================= */

    /**
     * Dead-simple SPP connect by paired name (contains, case-insensitive).
     * Uses GENERIC_SPP_UUID with a standard RFCOMM socket. This mirrors the
     * behavior of the version you said worked.
     */
    public void connectEscPosSimpleByName(String nameContains) throws IOException {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) throw new IOException("Bluetooth unavailable");
        if (nameContains == null || nameContains.trim().isEmpty()) throw new IOException("Empty name query");

        Set<BluetoothDevice> bonded;
        try {
            bonded = adapter.getBondedDevices();
        } catch (SecurityException se) {
            throw new IOException("Missing BLUETOOTH_CONNECT permission (Android 12+): " + se.getMessage(), se);
        }
        if (bonded == null || bonded.isEmpty()) throw new IOException("No paired Bluetooth devices");

        BluetoothDevice target = null;
        String q = nameContains.trim().toLowerCase();
        for (BluetoothDevice d : bonded) {
            String n = (d.getName() == null) ? "" : d.getName();
            if (n.toLowerCase().contains(q)) { target = d; break; }
        }
        if (target == null) {
            throw new IOException("Paired printer not found by name contains: \"" + nameContains + "\"\n"
                    + pairedDevicesSummary());
        }

        try { adapter.cancelDiscovery(); } catch (Throwable ignore) {}
        Log.d(TAG, "Simple connect to: " + target.getName() + " [" + target.getAddress() + "]");

        // Standard SPP socket (secure). If you ever need insecure, swap to createInsecureRfcommSocketToServiceRecord.
        socket = target.createRfcommSocketToServiceRecord(GENERIC_SPP_UUID);
        socket.connect();

        out = socket.getOutputStream();
        setConnected(target);

        // Quick sanity line so we know the channel is open (won’t hurt even if bitmap follows)
        try { printEscPosTestLine(); } catch (Exception ignore) {}
    }

    /** Handy summary for toasts/logs when matching fails. */
    public static String pairedDevicesSummary() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return "(No Bluetooth adapter)";
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) return "(No paired devices)";
        StringBuilder sb = new StringBuilder("Paired devices:\n");
        for (BluetoothDevice d : bonded) {
            sb.append("• ").append(d.getName()).append(" [").append(d.getAddress()).append("]\n");
        }
        return sb.toString();
    }

    /* =========================
       Your existing "smart" connectors (kept as-is)
       ========================= */

    public void connectByMac(String mac) throws IOException {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) throw new IOException("Bluetooth unavailable");
        try { adapter.cancelDiscovery(); } catch (Throwable ignore) {}
        BluetoothDevice dev = adapter.getRemoteDevice(mac);
        logChosenDevice(dev, "MAC");
        socket = openSocketExhaustive(dev);
        out = socket.getOutputStream();
        setConnected(dev);
    }

    public void connectByNameSmart(String query) throws IOException {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) throw new IOException("Bluetooth unavailable");
        if (query == null || query.trim().isEmpty()) throw new IOException("Empty name query");

        final String q = query.trim().toLowerCase();
        final Set<BluetoothDevice> bonded;
        try {
            bonded = adapter.getBondedDevices();
        } catch (SecurityException se) {
            throw new IOException("Missing BLUETOOTH_CONNECT permission (Android 12+). " + se.getMessage(), se);
        }
        if (bonded == null || bonded.isEmpty()) throw new IOException("No paired Bluetooth devices");

        BluetoothDevice best = null; int bestScore = -1;
        for (BluetoothDevice d : bonded) {
            String name = d.getName() == null ? "" : d.getName().trim();
            String ln = name.toLowerCase();
            int score = ln.equals(q) ? 3 : (ln.startsWith(q) ? 2 : (ln.contains(q) ? 1 : -1));
            if (score > bestScore) { bestScore = score; best = d; }
        }
        if (best == null || bestScore < 1) {
            StringBuilder sb = new StringBuilder("Paired device not found by name: \"")
                    .append(query).append("\".\n").append(pairedDevicesSummary());
            throw new IOException(sb.toString());
        }

        try { adapter.cancelDiscovery(); } catch (Throwable ignore) {}
        logChosenDevice(best, "name");
        socket = openSocketExhaustive(best);
        out = socket.getOutputStream();
        setConnected(best);
    }

    private void logChosenDevice(BluetoothDevice dev, String how) {
        String nm = dev != null ? dev.getName() : "(null)";
        String ad = dev != null ? dev.getAddress() : "(null)";
        Log.d(TAG, "Chosen by " + how + ": " + nm + " [" + ad + "]");
        if (Build.VERSION.SDK_INT >= 15 && dev != null) {
            try {
                ParcelUuid[] pu = dev.getUuids();
                if (pu != null && pu.length > 0) {
                    StringBuilder sb = new StringBuilder("Cached UUIDs: ");
                    for (ParcelUuid u : pu) sb.append(u.getUuid()).append(" ");
                    Log.d(TAG, sb.toString());
                } else {
                    Log.d(TAG, "Cached UUIDs: (none)");
                }
            } catch (SecurityException se) {
                Log.w(TAG, "getUuids() requires BLUETOOTH_CONNECT on Android 12+");
            }
        }
    }

    private void setConnected(BluetoothDevice dev) {
        connectedName = dev.getName();
        connectedAddr = dev.getAddress();
        Log.d(TAG, "Connected to: " + connectedName + " [" + connectedAddr + "]");
    }

    // Exhaustive connector (kept as a fallback; not used by the simple method above)
    private BluetoothSocket openSocketExhaustive(BluetoothDevice dev) throws IOException {
        IOException last = null;

        // A) Generic SPP UUID (insecure → secure)
        for (int insecureFirst = 1; insecureFirst >= 0; insecureFirst--) {
            try {
                UUID u = GENERIC_SPP_UUID;
                Log.d(TAG, "Trying UUID (" + (insecureFirst == 1 ? "insecure" : "secure") + "): " + u);
                BluetoothSocket s = (insecureFirst == 1)
                        ? dev.createInsecureRfcommSocketToServiceRecord(u)
                        : dev.createRfcommSocketToServiceRecord(u);
                if (connectWithTimeout(s, CONNECT_TIMEOUT_UUID_MS)) {
                    Log.d(TAG, "Connected via UUID " + (insecureFirst == 1 ? "insecure" : "secure") + " " + u);
                    return s;
                } else {
                    safeClose(s);
                    Log.w(TAG, "UUID (" + (insecureFirst == 1 ? "insecure" : "secure") + ") timed out: " + u);
                }
            } catch (Throwable t) {
                last = asIo(t);
                Log.w(TAG, "UUID attempt failed: " + t.getMessage());
            }
        }

        // B) All advertised UUIDs (other than generic SPP)
        try {
            ParcelUuid[] cached = dev.getUuids();
            if (cached != null) {
                for (ParcelUuid pu : cached) {
                    UUID u = pu.getUuid();
                    if (GENERIC_SPP_UUID.equals(u)) continue;
                    for (int insecureFirst = 1; insecureFirst >= 0; insecureFirst--) {
                        try {
                            Log.d(TAG, "Trying UUID (" + (insecureFirst == 1 ? "insecure" : "secure") + "): " + u);
                            BluetoothSocket s = (insecureFirst == 1)
                                    ? dev.createInsecureRfcommSocketToServiceRecord(u)
                                    : dev.createRfcommSocketToServiceRecord(u);
                            if (connectWithTimeout(s, CONNECT_TIMEOUT_UUID_MS)) {
                                Log.d(TAG, "Connected via UUID " + (insecureFirst == 1 ? "insecure" : "secure") + " " + u);
                                return s;
                            } else {
                                safeClose(s);
                                Log.w(TAG, "UUID (" + (insecureFirst == 1 ? "insecure" : "secure") + ") timed out: " + u);
                            }
                        } catch (Throwable t) {
                            last = asIo(t);
                            Log.w(TAG, "UUID attempt failed " + u + ": " + t.getMessage());
                        }
                    }
                }
            }
        } catch (SecurityException se) {
            Log.w(TAG, "getUuids() needs BLUETOOTH_CONNECT: " + se.getMessage());
        }

        // C1) Preferred channels first
        for (int ch : PREFERRED_CHANNELS) {
            BluetoothSocket s = null;
            try {
                Method m = dev.getClass().getMethod("createRfcommSocket", int.class);
                s = (BluetoothSocket) m.invoke(dev, ch);
                Log.d(TAG, "Trying reflection channel " + ch + " …");
                if (connectWithTimeout(s, CONNECT_TIMEOUT_CHAN_MS)) {
                    Log.d(TAG, "Connected via reflection channel " + ch);
                    return s;
                } else {
                    Log.w(TAG, "Channel " + ch + " timed out");
                    safeClose(s);
                }
            } catch (Throwable t) {
                last = asIo(t);
                Log.w(TAG, "Channel " + ch + " failed: " + t.getMessage());
                safeClose(s);
            }
        }

        // C2) Sweep remaining channels
        sweep:
        for (int ch = 1; ch <= REFLECTION_MAX_CHANNEL; ch++) {
            for (int tried : PREFERRED_CHANNELS) if (tried == ch) continue sweep;
            BluetoothSocket s = null;
            try {
                Method m = dev.getClass().getMethod("createRfcommSocket", int.class);
                s = (BluetoothSocket) m.invoke(dev, ch);
                Log.d(TAG, "Trying reflection channel " + ch + " …");
                if (connectWithTimeout(s, CONNECT_TIMEOUT_CHAN_MS)) {
                    Log.d(TAG, "Connected via reflection channel " + ch);
                    return s;
                } else {
                    Log.w(TAG, "Channel " + ch + " timed out");
                    safeClose(s);
                }
            } catch (Throwable t) {
                last = asIo(t);
                Log.w(TAG, "Channel " + ch + " failed: " + t.getMessage());
                safeClose(s);
            }
        }

        throw (last != null ? last : new IOException("All RFCOMM attempts failed (is the printer in SPP and free?)"));
    }

    private static boolean connectWithTimeout(final BluetoothSocket s, int timeoutMs) throws IOException {
        Future<Boolean> f = CONNECT_EXEC.submit(() -> { s.connect(); return true; });
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException(cause);
        } catch (TimeoutException te) {
            try { s.close(); } catch (Throwable ignore) {}
            return false;
        } catch (InterruptedException ie) {
            try { s.close(); } catch (Throwable ignore) {}
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void safeClose(BluetoothSocket s) {
        if (s != null) { try { s.close(); } catch (Throwable ignore) {} }
    }

    private static IOException asIo(Throwable t) {
        return (t instanceof IOException) ? (IOException)t : new IOException(t);
    }

    public String getConnectedName()    { return connectedName; }
    public String getConnectedAddress() { return connectedAddr; }
    public boolean isConnected()        { return socket != null && socket.isConnected(); }

    public void close() {
        try { if (out != null) out.close(); } catch (Exception ignore) {}
        try { if (socket != null) socket.close(); } catch (Exception ignore) {}
        out = null; socket = null; connectedName = null; connectedAddr = null;
        Log.d(TAG, "Socket closed");
    }

    /* =========================
       Core write helpers
       ========================= */

    private void cmd(String s) throws IOException {
        rawChunked((s + "\r\n").getBytes(StandardCharsets.US_ASCII), 256);
    }

    private void raw(byte[] b) throws IOException {
        rawChunked(b, 1024);
    }

    private void rawChunked(byte[] b, int chunk) throws IOException {
        if (out == null) throw new IOException("Not connected");
        int off = 0;
        while (off < b.length) {
            int len = Math.min(chunk, b.length - off);
            out.write(b, off, len);
            out.flush();
            off += len;
            try { Thread.sleep(2); } catch (InterruptedException ignore) {}
        }
    }

    /* =========================
       TSPL (label) printing
       ========================= */

    public void printBitmapLabel(Bitmap bmp, int labelWidthMm, int labelHeightMm, int dpi, int gapMm) throws IOException {
        if (out == null) throw new IOException("Not connected");

        int targetW = Math.round(labelWidthMm * dpi / 25.4f);
        int targetH = Math.round(labelHeightMm * dpi / 25.4f);

        Bitmap scaled = scaleToWidth(bmp, targetW);
        if (scaled.getHeight() > targetH) {
            scaled = Bitmap.createBitmap(scaled, 0, 0, scaled.getWidth(), targetH);
        }

        Bitmap mono = toMono(scaled, 160);

        final int w = mono.getWidth();
        final int h = mono.getHeight();
        final int bytesPerRow = (w + 7) / 8;
        byte[] raster = new byte[bytesPerRow * h];

        int i = 0;
        for (int y = 0; y < h; y++) {
            int bit = 0, cur = 0;
            for (int x = 0; x < w; x++) {
                int c = mono.getPixel(x, y);
                boolean black = (Color.red(c) == 0);
                cur = (cur << 1) | (black ? 1 : 0);
                bit++;
                if (bit == 8) { raster[i++] = (byte) cur; bit = 0; cur = 0; }
            }
            if (bit != 0) { cur <<= (8 - bit); raster[i++] = (byte) cur; }
        }

        cmd("SIZE " + labelWidthMm + " mm, " + labelHeightMm + " mm");
        cmd("GAP " + gapMm + " mm,0 mm"); // GAP 0,0 for continuous
        cmd("REFERENCE 0,0");
        cmd("DIRECTION 1");
        cmd("CLS");
        cmd("BITMAP 0,0," + bytesPerRow + "," + h + ",1");
        raw(raster);
        cmd("PRINT 1");
    }

    /* =========================
       ESC/POS (receipt) printing
       ========================= */

    public void printBitmapEscPos(Bitmap src, int targetWidthDots, int feedLines) throws IOException {
        if (out == null) throw new IOException("Not connected");

        Bitmap scaled = scaleToWidth(src, targetWidthDots);
        Bitmap mono   = toMono(scaled, 160);

        int w = mono.getWidth(), h = mono.getHeight();
        int bpr = (w + 7) / 8;
        byte[] raster = new byte[bpr * h];

        int i = 0;
        for (int y = 0; y < h; y++) {
            int bit = 0, cur = 0;
            for (int x = 0; x < w; x++) {
                int px = mono.getPixel(x, y);
                boolean black = (Color.red(px) == 0);
                cur = (cur << 1) | (black ? 1 : 0);
                bit++;
                if (bit == 8) { raster[i++] = (byte) cur; bit = 0; cur = 0; }
            }
            if (bit != 0) { cur <<= (8 - bit); raster[i++] = (byte) cur; }
        }

        raw(new byte[]{0x1B, 0x40});         // ESC @ (init)
        raw(new byte[]{0x1B, 0x61, 0x00});   // left align
        // PC437 just in case you print text too:
        raw(new byte[]{0x1B, 0x74, 0x00});   // ESC t 0 (USA, PC437)

        int xL = bpr & 0xFF, xH = (bpr >> 8) & 0xFF;
        int yL = h & 0xFF,   yH = (h >> 8) & 0xFF;
        raw(new byte[]{0x1D, 0x76, 0x30, 0x00, (byte)xL, (byte)xH, (byte)yL, (byte)yH});
        raw(raster);
        raw(new byte[]{0x1B, 0x64, (byte)Math.max(1, feedLines)}); // feed n lines
    }

    /** Tiny smoke test so you can see output as soon as the socket opens. */
    public void printEscPosTestLine() throws IOException {
        if (out == null) throw new IOException("Not connected");
        raw(new byte[]{0x1B, 0x40}); // init
        raw(new byte[]{0x1B, 0x61, 0x00}); // left
        raw(new byte[]{0x1B, 0x74, 0x00}); // PC437
        raw("** ESC/POS OK **".getBytes(StandardCharsets.US_ASCII));
        raw(new byte[]{0x0A, 0x0A});
    }

    /* =========================
       Rendering helpers
       ========================= */

    public static Bitmap renderViewToBitmap(View v, int widthPx) {
        int wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY);
        int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        v.measure(wSpec, hSpec);
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
        Bitmap b = Bitmap.createBitmap(v.getMeasuredWidth(), v.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.WHITE);
        v.draw(c);
        return b;
    }

    private static Bitmap scaleToWidth(Bitmap src, int targetW) {
        if (src.getWidth() == targetW) return src;
        float r = targetW / (float) src.getWidth();
        int h = Math.max(1, Math.round(src.getHeight() * r));
        return Bitmap.createScaledBitmap(src, targetW, h, true);
    }

    private static Bitmap toMono(Bitmap src, int threshold) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = src.getPixel(x, y);
                int g = (int) (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c));
                out.setPixel(x, y, (g < threshold) ? Color.BLACK : Color.WHITE);
            }
        }
        return out;
    }
}










