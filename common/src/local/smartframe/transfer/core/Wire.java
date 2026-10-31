package local.smartframe.transfer.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Small, bounded HTTP/1.1 transport. One request per connection. */
public final class Wire {
    public static final long MAX_PHOTO = 50L * 1024 * 1024;
    public static final int PORT = 8844;
    public static String hex(byte[] bytes) {
        StringBuilder b = new StringBuilder();
        for (byte v : bytes) b.append(String.format(Locale.US, "%02x", v & 255));
        return b.toString();
    }
    public static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String hash(String s) { return hex(digest().digest(s.getBytes(StandardCharsets.UTF_8))); }
    public static String hash(InputStream in) throws IOException {
        MessageDigest d = digest(); byte[] b = new byte[32768]; int n;
        while ((n = in.read(b)) != -1) d.update(b, 0, n);
        return hex(d.digest());
    }
    public static boolean validHash(String h) { return h != null && h.matches("[0-9a-f]{64}"); }
    public static boolean equal(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
    public static String randomToken() {
        byte[] b = new byte[32]; new SecureRandom().nextBytes(b); return hex(b);
    }
    public static String shortFingerprint(String hash) {
        return hash.substring(0, 4).toUpperCase(Locale.US) + " " + hash.substring(4, 8).toUpperCase(Locale.US) + " " + hash.substring(8, 12).toUpperCase(Locale.US);
    }
    public static final class Request {
        public String method, path;
        public long length;
        public final Map<String,String> headers = new HashMap<>();
        public String header(String key) { return headers.get(key); }
    }
    private static String line(InputStream in, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                byte[] raw = b.toByteArray();
                if (raw.length == 0 || raw[raw.length - 1] != '\r') throw new IOException("Invalid line ending");
                return new String(raw, 0, raw.length - 1, StandardCharsets.US_ASCII);
            }
            if (b.size() >= max) throw new IOException("Header too large");
            b.write(c);
        }
        throw new EOFException("Incomplete headers");
    }
    public static Request request(InputStream in) throws IOException {
        Request r = new Request(); String first = line(in, 2048);
        String[] parts = first.split(" ");
        if (parts.length != 3 || !parts[2].equals("HTTP/1.1")) throw new IOException("Invalid HTTP request");
        r.method = parts[0]; r.path = parts[1];
        int total = first.length();
        for (int i = 0; ; i++) {
            String s = line(in, 4096); total += s.length() + 2;
            if (total > 16384 || i > 40) throw new IOException("Too many headers");
            if (s.isEmpty()) break;
            int p = s.indexOf(':'); if (p < 1) throw new IOException("Invalid header");
            String key = s.substring(0,p).toLowerCase(Locale.US);
            if (!key.matches("[a-z0-9-]+") || r.headers.containsKey(key)) throw new IOException("Duplicate/invalid header");
            r.headers.put(key, s.substring(p+1).trim());
        }
        if (r.headers.containsKey("transfer-encoding")) throw new IOException("Chunked requests not supported");
        String len = r.header("content-length");
        if (len != null) {
            if (!len.matches("[0-9]{1,10}")) throw new IOException("Invalid content length");
            try { r.length = Long.parseLong(len); } catch (NumberFormatException e) { throw new IOException("Invalid content length"); }
        } else if (r.method.equals("POST")) throw new IOException("Content length required");
        if (r.length > MAX_PHOTO) throw new IOException("사진은 50MB 이하만 전송할 수 있어요.");
        return r;
    }
    public static void copyExactly(InputStream in, OutputStream out, long length) throws IOException {
        if (length < 0 || length > MAX_PHOTO) throw new IOException("Invalid body size");
        byte[] b = new byte[32768]; long left = length;
        while (left > 0) {
            int n = in.read(b, 0, (int)Math.min(b.length, left));
            if (n < 0) throw new EOFException("사진 전송이 중간에 끊겼어요.");
            if (n == 0) continue;
            out.write(b,0,n); left -= n;
        }
    }
    public static String body(InputStream in, long length) throws IOException {
        if (length > 65536) throw new IOException("Request too large");
        ByteArrayOutputStream b = new ByteArrayOutputStream(); copyExactly(in,b,length);
        return new String(b.toByteArray(),StandardCharsets.UTF_8);
    }
    public static void response(OutputStream out, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + code + " Result\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes); out.flush();
    }
}
