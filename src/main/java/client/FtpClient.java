package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** FTP client tu cai bang socket (khong dung thu vien ngoai). */
public class FtpClient {

    public interface LogListener { void onLog(String line); }
    public interface ProgressListener { void onProgress(long done, long total); }

    public static class CancelledException extends IOException {
        public CancelledException() { super("Transfer cancelled"); }
    }

    private static class Reply {
        final int code;
        final String message;
        Reply(int code, String message) { this.code = code; this.message = message; }
    }

    private static final Pattern PASV_PATTERN = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)");
    private static final Pattern UNIX_LINE = Pattern.compile(
            "^([\\-dl])[rwxstST\\-]{9}\\S*\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+"
          + "(\\w{3}\\s+\\d{1,2}\\s+(?:\\d{4}|\\d{1,2}:\\d{2}))\\s+(.+)$");
    private static final Pattern DOS_LINE = Pattern.compile(
            "^(\\d{2}-\\d{2}-\\d{2,4})\\s+(\\d{2}:\\d{2}\\s*[AaPp][Mm])\\s+(<DIR>|\\d+)\\s+(.+)$");

    private volatile Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String host;
    private LogListener logListener;
    private volatile boolean cancelled;

    public void setLogListener(LogListener l) { this.logListener = l; }

    public boolean isConnected() {
        Socket s = socket;
        return s != null && s.isConnected() && !s.isClosed();
    }

    public void cancel() { cancelled = true; }
    public void resetCancel() { cancelled = false; }

    private void log(String s) {
        LogListener l = logListener;
        if (l != null) l.onLog(s);
    }

    // ------------------------------------------------------------------ ket noi

    public synchronized void connect(String host, int port) throws IOException {
        disconnect();
        this.host = host;
        log("Status: Connecting to " + host + ":" + port + "...");
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 10000);
        s.setSoTimeout(30000);
        socket = s;
        reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
        Reply r = readReply();
        if (r.code != 220) throw new IOException("Server refused connection: " + r.message);
    }

    public synchronized void login(String user, String pass) throws IOException {
        Reply r = command("USER " + user);
        if (r.code == 331 || r.code == 332) r = command("PASS " + pass);
        if (r.code != 230) throw new IOException("Login failed: " + r.message);
        command("TYPE I");
    }

    public synchronized void quit() {
        if (isConnected()) {
            try { command("QUIT"); } catch (IOException ignored) { }
        }
        disconnect();
    }

    public synchronized void disconnect() {
        Socket s = socket;
        socket = null;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) { }
            log("Status: Disconnected from server");
        }
    }

    // ------------------------------------------------------------------ lenh co ban

    private Reply readReply() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Connection closed by server");
        log("Response: " + line);
        int code = 0;
        if (line.length() >= 3) {
            try { code = Integer.parseInt(line.substring(0, 3)); } catch (NumberFormatException ignored) { }
        }
        String msg = line.length() > 4 ? line.substring(4) : "";
        if (line.length() > 3 && line.charAt(3) == '-') {
            String end = line.substring(0, 3) + " ";
            while (true) {
                String l = reader.readLine();
                if (l == null) throw new IOException("Connection closed by server");
                log("Response: " + l);
                if (l.startsWith(end)) break;
            }
        }
        return new Reply(code, msg);
    }

    private Reply command(String cmd) throws IOException {
        if (writer == null || !isConnected()) throw new IOException("Not connected to a server");
        log("Command: " + (cmd.startsWith("PASS ") ? "PASS ****" : cmd));
        writer.write(cmd + "\r\n");
        writer.flush();
        return readReply();
    }

    public synchronized String pwd() throws IOException {
        Reply r = command("PWD");
        if (r.code != 257) throw new IOException("PWD failed: " + r.message);
        int a = r.message.indexOf('"');
        int b = r.message.lastIndexOf('"');
        if (a >= 0 && b > a) return r.message.substring(a + 1, b);
        return "/";
    }

    public synchronized void cwd(String path) throws IOException {
        Reply r = command("CWD " + path);
        if (r.code != 250) throw new IOException("Cannot open directory: " + r.message);
    }

    public synchronized void mkd(String path) throws IOException {
        Reply r = command("MKD " + path);
        if (r.code != 257) throw new IOException("Cannot create directory: " + r.message);
    }

    public synchronized void rmd(String path) throws IOException {
        Reply r = command("RMD " + path);
        if (r.code != 250) throw new IOException("Cannot remove directory: " + r.message);
    }

    public synchronized void dele(String path) throws IOException {
        Reply r = command("DELE " + path);
        if (r.code != 250) throw new IOException("Cannot delete file: " + r.message);
    }

    public synchronized void rename(String from, String to) throws IOException {
        Reply r = command("RNFR " + from);
        if (r.code != 350) throw new IOException("Rename failed: " + r.message);
        r = command("RNTO " + to);
        if (r.code != 250) throw new IOException("Rename failed: " + r.message);
    }

    public synchronized long size(String path) {
        try {
            Reply r = command("SIZE " + path);
            if (r.code == 213) return Long.parseLong(r.message.trim());
        } catch (Exception ignored) { }
        return -1;
    }

    /** Xoa de quy (file hoac thu muc, ke ca thu muc co noi dung). */
    public synchronized void deleteRecursive(String path, boolean isDir) throws IOException {
        if (isDir) {
            for (FileItem it : list(path)) {
                deleteRecursive(join(path, it.getName()), it.isDirectory());
            }
            rmd(path);
        } else {
            dele(path);
        }
    }

    public static String join(String dir, String name) {
        if (dir == null || dir.isEmpty()) return name;
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    // ------------------------------------------------------------------ ket noi du lieu

    private Socket openPassive() throws IOException {
        Reply r = command("PASV");
        if (r.code != 227) throw new IOException("PASV failed: " + r.message);
        Matcher m = PASV_PATTERN.matcher(r.message);
        if (!m.find()) throw new IOException("Cannot parse PASV reply: " + r.message);
        int port = Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 10000); // dung host cua ket noi dieu khien (tranh loi NAT)
        s.setSoTimeout(30000);
        return s;
    }

    private static void closeQuietly(Socket s) {
        try { s.close(); } catch (IOException ignored) { }
    }

    // ------------------------------------------------------------------ LIST

    /** Liet ke thu muc (path = null: thu muc hien tai). */
    public synchronized List<FileItem> list(String path) throws IOException {
        List<String> lines = new ArrayList<>();
        Socket ds = openPassive();
        try {
            Reply r = command(path == null ? "LIST" : "LIST " + path);
            if (r.code < 100 || r.code >= 200) throw new IOException("Cannot list directory: " + r.message);
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(ds.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.trim().isEmpty()) lines.add(line);
                }
            }
        } finally {
            closeQuietly(ds);
        }
        Reply done = readReply();
        if (done.code != 226 && done.code != 250) throw new IOException("Listing failed: " + done.message);

        List<FileItem> items = new ArrayList<>();
        for (String line : lines) {
            FileItem it = parseLine(line);
            if (it != null) items.add(it);
        }
        return items;
    }

    private static FileItem parseLine(String line) {
        Matcher m = UNIX_LINE.matcher(line);
        if (m.matches()) {
            String type = m.group(1);
            String name = m.group(4);
            if ("l".equals(type)) {
                int arrow = name.indexOf(" -> ");
                if (arrow > 0) name = name.substring(0, arrow);
            }
            if (name.equals(".") || name.equals("..")) return null;
            return new FileItem(name, "d".equals(type), Long.parseLong(m.group(2)), m.group(3));
        }
        m = DOS_LINE.matcher(line);
        if (m.matches()) {
            String name = m.group(4);
            boolean dir = "<DIR>".equals(m.group(3));
            if (name.equals(".") || name.equals("..")) return null;
            return new FileItem(name, dir, dir ? -1 : Long.parseLong(m.group(3)), m.group(1) + " " + m.group(2));
        }
        return null;
    }

    // ------------------------------------------------------------------ DOWNLOAD / UPLOAD

    public synchronized void download(String remotePath, File local, ProgressListener pl) throws IOException {
        long total = size(remotePath);
        Socket ds = openPassive();
        boolean wasCancelled = false;
        boolean failed = true;
        boolean opened = false;
        try {
            Reply r = command("RETR " + remotePath);
            if (r.code < 100 || r.code >= 200) throw new IOException("Download failed: " + r.message);
            try (InputStream is = ds.getInputStream(); OutputStream os = new FileOutputStream(local)) {
                opened = true;
                byte[] buf = new byte[16384];
                int n;
                long done = 0, lastReport = 0;
                while ((n = is.read(buf)) != -1) {
                    if (cancelled) { wasCancelled = true; break; }
                    os.write(buf, 0, n);
                    done += n;
                    if (pl != null && done - lastReport >= 65536) {
                        pl.onProgress(done, total);
                        lastReport = done;
                    }
                }
                if (pl != null && !wasCancelled) pl.onProgress(done, total);
            }
            failed = false;
        } finally {
            closeQuietly(ds);
            if (opened && (failed || wasCancelled)) local.delete();
        }
        Reply fin = readReply();
        if (wasCancelled) throw new CancelledException();
        if (fin.code != 226 && fin.code != 250) {
            local.delete();
            throw new IOException("Download failed: " + fin.message);
        }
    }

    public synchronized void upload(File local, String remotePath, ProgressListener pl) throws IOException {
        long total = local.length();
        Socket ds = openPassive();
        boolean wasCancelled = false;
        try {
            Reply r = command("STOR " + remotePath);
            if (r.code < 100 || r.code >= 200) throw new IOException("Upload failed: " + r.message);
            try (InputStream is = new FileInputStream(local); OutputStream os = ds.getOutputStream()) {
                byte[] buf = new byte[16384];
                int n;
                long done = 0, lastReport = 0;
                while ((n = is.read(buf)) != -1) {
                    if (cancelled) { wasCancelled = true; break; }
                    os.write(buf, 0, n);
                    done += n;
                    if (pl != null && done - lastReport >= 65536) {
                        pl.onProgress(done, total);
                        lastReport = done;
                    }
                }
                os.flush();
                if (pl != null && !wasCancelled) pl.onProgress(done, total);
            }
        } finally {
            closeQuietly(ds);
        }
        Reply fin = readReply();
        if (wasCancelled) throw new CancelledException();
        if (fin.code != 226 && fin.code != 250) throw new IOException("Upload failed: " + fin.message);
    }
}
