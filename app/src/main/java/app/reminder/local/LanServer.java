package app.reminder.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * เว็บเซิร์ฟเวอร์เล็กๆ ในมือถือ (Java ล้วน ไม่พึ่ง Android) ให้คอมพิวเตอร์ในวงแลนเดียวกันเข้ามาจัดการรายการ
 * ทุกคำขอที่ขึ้นต้นด้วย /api/ ต้องมีหัว X-Pin ตรงกับรหัสที่แสดงบนมือถือ
 */
public final class LanServer {
    public interface Handler {
        Response handle(String method, String path, String body) throws Exception;
    }

    public static final class Response {
        public final int status;
        public final String type;
        public final byte[] body;
        public Response(int status, String type, byte[] body) { this.status = status; this.type = type; this.body = body; }
        public static Response json(int status, String s) { return new Response(status, "application/json; charset=utf-8", s.getBytes(StandardCharsets.UTF_8)); }
        public static Response html(byte[] b) { return new Response(200, "text/html; charset=utf-8", b); }
    }

    private static final int MAX_HEADER = 16 * 1024, MAX_BODY = 64 * 1024, MAX_FAILS = 8;

    private final String pin;
    private final Handler handler;
    private final AtomicInteger fails = new AtomicInteger();
    private volatile boolean running;
    private ServerSocket server;
    private ExecutorService pool;

    public LanServer(String pin, Handler handler) {
        this.pin = pin;
        this.handler = handler;
    }

    /** เริ่มฟังที่พอร์ตแรกที่ว่างในช่วง from..to คืนพอร์ตที่ใช้ */
    public synchronized int start(int from, int to) throws IOException {
        IOException last = null;
        for (int p = from; p <= to; p++) {
            try {
                ServerSocket s = new ServerSocket();
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress(p));
                server = s;
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        if (server == null) throw last != null ? last : new IOException("no port");
        running = true;
        pool = Executors.newFixedThreadPool(4, new ThreadFactory() {
            @Override public Thread newThread(Runnable r) { Thread t = new Thread(r, "lan-worker"); t.setDaemon(true); return t; }
        });
        final ServerSocket ss = server;
        Thread acc = new Thread(new Runnable() {
            @Override public void run() {
                while (running) {
                    try {
                        final Socket c = ss.accept();
                        pool.execute(new Runnable() { @Override public void run() { serve(c); } });
                    } catch (Exception e) {
                        if (!running) return;
                    }
                }
            }
        }, "lan-accept");
        acc.setDaemon(true);
        acc.start();
        return ss.getLocalPort();
    }

    public synchronized void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        if (pool != null) pool.shutdownNow();
        server = null;
        pool = null;
    }

    private void serve(Socket c) {
        try (Socket s = c) {
            s.setSoTimeout(10000);
            InputStream in = s.getInputStream();
            Response r = process(in);
            write(s.getOutputStream(), r);
        } catch (Exception ignored) {
            // ไคลเอนต์หลุด/ส่งมาผิดรูปแบบ ไม่ต้องทำอะไร
        }
    }

    private Response process(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int m = 0, b;
        while (m < 4) {
            b = in.read();
            if (b < 0) throw new IOException("eof");
            head.write(b);
            if (head.size() > MAX_HEADER) return Response.json(431, "{\"error\":\"header too large\"}");
            if ((m == 0 || m == 2) && b == '\r') m++;
            else if ((m == 1 || m == 3) && b == '\n') m++;
            else m = (b == '\r') ? 1 : 0;
        }
        String[] lines = new String(head.toByteArray(), StandardCharsets.ISO_8859_1).split("\r\n");
        String[] rl = lines[0].split(" ");
        if (rl.length < 2) return Response.json(400, "{\"error\":\"bad request\"}");
        String method = rl[0].toUpperCase(Locale.ROOT);
        String path = rl[1];
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        Map<String, String> h = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int k = lines[i].indexOf(':');
            if (k > 0) h.put(lines[i].substring(0, k).trim().toLowerCase(Locale.ROOT), lines[i].substring(k + 1).trim());
        }
        int len = 0;
        try { len = Integer.parseInt(h.getOrDefault("content-length", "0")); } catch (NumberFormatException e) { return Response.json(400, "{\"error\":\"bad length\"}"); }
        if (len < 0 || len > MAX_BODY) return Response.json(413, "{\"error\":\"body too large\"}");
        byte[] body = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(body, off, len - off);
            if (n < 0) throw new IOException("eof");
            off += n;
        }
        if (path.startsWith("/api/")) {
            if (fails.get() >= MAX_FAILS) return Response.json(429, "{\"error\":\"locked\"}");
            String given = h.getOrDefault("x-pin", "");
            if (!MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), pin.getBytes(StandardCharsets.UTF_8))) {
                fails.incrementAndGet();
                return Response.json(401, "{\"error\":\"bad pin\"}");
            }
            fails.set(0);
        }
        try {
            return handler.handle(method, path, new String(body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Response.json(500, "{\"error\":\"server error\"}");
        }
    }

    private static void write(OutputStream out, Response r) throws IOException {
        String reason = r.status == 200 ? "OK" : "Error";
        String head = "HTTP/1.1 " + r.status + " " + reason + "\r\n"
                + "Content-Type: " + r.type + "\r\n"
                + "Content-Length: " + r.body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(r.body);
        out.flush();
    }
}
