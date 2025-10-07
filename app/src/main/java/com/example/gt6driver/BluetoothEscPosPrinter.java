package com.example.gt6driver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.Charset;
public class BluetoothEscPosPrinter {

    private static final String TAG = "BT_ESC_POS";
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothSocket socket;
    private OutputStream out;
    private String connectedName = null;

    /** Connect by MAC address. */
    public void connect(String macAddress) throws IOException {
        ensureBt();
        BluetoothDevice device = adapter.getRemoteDevice(macAddress);
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        adapter.cancelDiscovery();
        socket.connect();
        out = socket.getOutputStream();
        connectedName = device.getName();
        Log.d(TAG, "Connected (MAC) to " + connectedName + " [" + macAddress + "]");
        initPc437();
    }

    /** Connect by paired device name containing the query (case-insensitive). */
    public void connectByName(String nameContains) throws IOException {
        ensureBt();
        Set<BluetoothDevice> paired = adapter.getBondedDevices();
        BluetoothDevice target = null;
        if (paired != null) {
            for (BluetoothDevice d : paired) {
                String n = d.getName();
                if (n != null && n.toLowerCase().contains(nameContains.toLowerCase())) {
                    target = d; break;
                }
            }
        }
        if (target == null) {
            throw new IOException("Paired printer not found by name: " + nameContains);
        }
        socket = target.createRfcommSocketToServiceRecord(SPP_UUID);
        adapter.cancelDiscovery();
        socket.connect();
        out = socket.getOutputStream();
        connectedName = target.getName();
        Log.d(TAG, "Connected (name) to " + connectedName + " [" + target.getAddress() + "]");
        initPc437();
    }

    public String getConnectedName() { return connectedName; }
    public boolean isConnected() { return socket != null && socket.isConnected(); }

    public void close() {
        try { if (out != null) out.close(); } catch (Exception ignore) {}
        try { if (socket != null) socket.close(); } catch (Exception ignore) {}
        out = null; socket = null; connectedName = null;
    }

    /** Initialize printer and set PC437 (as you mentioned). */
    private void initPc437() throws IOException {
        write(new byte[]{0x1B, 0x40});        // ESC @  (init)
        write(new byte[]{0x1B, 0x61, 0x00});  // ESC a 0 (left align)
        write(new byte[]{0x1B, 0x74, 0x00});  // ESC t 0 (codepage PC437)
    }

    /** Print a bitmap with ESC/POS raster (GS v 0). */
    public void printBitmap(Bitmap source, int targetWidthDots, boolean addFeedAndCut) throws IOException {
        if (out == null) throw new IOException("Not connected");

        // Scale to printer width
        Bitmap scaled = scaleToWidth(source, targetWidthDots);

        // Mono (threshold)
        Bitmap mono = toMono(scaled);

        int w = mono.getWidth();
        int h = mono.getHeight();
        int bytesPerRow = (w + 7) / 8;
        byte[] imageBytes = new byte[bytesPerRow * h];

        int idx = 0;
        for (int y = 0; y < h; y++) {
            int bit = 0, cur = 0;
            for (int x = 0; x < w; x++) {
                int pixel = mono.getPixel(x, y);
                boolean black = (Color.red(pixel) == 0); // strictly 0 or 255
                cur = (cur << 1) | (black ? 1 : 0);
                bit++;
                if (bit == 8) {
                    imageBytes[idx++] = (byte) cur;
                    bit = 0; cur = 0;
                }
            }
            if (bit != 0) {
                cur <<= (8 - bit);
                imageBytes[idx++] = (byte) cur;
            }
        }

        // Ensure init + PC437 each job (safe)
        initPc437();

        // GS v 0 m xL xH yL yH [data]
        int xL = bytesPerRow & 0xFF, xH = (bytesPerRow >> 8) & 0xFF;
        int yL = h & 0xFF,         yH = (h >> 8) & 0xFF;

        write(new byte[]{0x1D, 0x76, 0x30, 0x00, (byte) xL, (byte) xH, (byte) yL, (byte) yH});
        write(imageBytes);

        if (addFeedAndCut) {
            write(new byte[]{0x1B, 0x64, 0x03}); // feed 3 lines
            write(new byte[]{(byte)0x1D, 0x56, 0x00}); // full cut (if supported)
        }
    }

    private void write(byte[] data) throws IOException {
        out.write(data);
        out.flush();
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
    }

    private static Bitmap scaleToWidth(Bitmap src, int targetW) {
        if (src.getWidth() == targetW) return src;
        float r = targetW / (float) src.getWidth();
        int h = Math.max(1, Math.round(src.getHeight() * r));
        return Bitmap.createScaledBitmap(src, targetW, h, true);
    }

    private static Bitmap toMono(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = src.getPixel(x, y);
                int gray = (int)(0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c));
                out.setPixel(x, y, (gray < 160) ? Color.BLACK : Color.WHITE);
            }
        }
        return out;
    }

    private void ensureBt() throws IOException {
        if (adapter == null) throw new IOException("Bluetooth not supported");
        if (!adapter.isEnabled()) throw new IOException("Bluetooth disabled");
    }
    public void printText(String text) throws IOException {
        if (out == null) throw new IOException("Not connected");

        // Initialize printer
        write(new byte[]{0x1B, 0x40});      // ESC @
        // Select PC437 (USA: Standard Europe)
        write(new byte[]{0x1B, 0x74, 0x00}); // ESC t 0
        // Left align
        write(new byte[]{0x1B, 0x61, 0x00}); // ESC a 0

        // Send text as US-ASCII (avoids StandardCharsets import issues)
        write(text.getBytes(Charset.forName("US-ASCII")));

        // Ensure there's a newline if caller didn't add one
        if (!text.endsWith("\n")) write(new byte[]{0x0A});
    }
}
