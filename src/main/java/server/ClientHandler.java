package server;

import db.TransferLogDao;
import db.User;
import db.UserDao;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;

/** Xu ly 1 phien FTP (1 thread cho moi client). */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final DataConnection data = new DataConnection();
    private BufferedReader in;
    private BufferedWriter out;

    private User user;
    private String pendingUser;
    private int failedLogins = 0;
    private File homeRoot;          // thu muc goc that cua user
    private String cwd = "/";       // duong dan ao hien tai (bat dau tu "/")
    private String renameFrom;      // duong dan ao cho RNFR/RNTO
    private long restOffset = 0;    // cho lenh REST (resume)

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        String who = socket.getInetAddress().getHostAddress();
        System.out.println("[+] Ket noi tu " + who);
        try {
            socket.setSoTimeout(10 * 60 * 1000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            send("220 Welcome to MyFTP Server");

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int sp = line.indexOf(' ');
                String cmd = (sp < 0 ? line : line.substring(0, sp)).toUpperCase();
                String arg = sp < 0 ? "" : line.substring(sp + 1).trim();
                System.out.println("[" + who + "] " + (cmd.equals("PASS") ? "PASS ****" : line));
                try {
                    if (!handle(cmd, arg)) break;
                } catch (SecurityException e) {
                    send("550 Permission denied");
                }
            }
        } catch (IOException e) {
            // client ngat ket noi dot ngot
        } finally {
            data.close();
            try { socket.close(); } catch (IOException ignored) { }
            System.out.println("[-] Ngat ket noi " + who);
        }
    }

    private void send(String msg) throws IOException {
        out.write(msg + "\r\n");
        out.flush();
    }

    /** @return false neu can dong ket noi */
    private boolean handle(String cmd, String arg) throws IOException {
        switch (cmd) {
            case "USER":
                pendingUser = arg;
                send("331 Password required");
                return true;
            case "PASS":
                return doPass(arg);
            case "QUIT":
                send("221 Goodbye");
                return false;
            case "SYST":
                send("215 UNIX Type: L8");
                return true;
            case "FEAT":
                send("211-Features:");
                send(" UTF8");
                send(" SIZE");
                send(" REST STREAM");
                send("211 End");
                return true;
            case "OPTS":
            case "NOOP":
                send("200 OK");
                return true;
            default:
                if (user == null) {
                    send("530 Please login with USER and PASS");
                } else {
                    doLoggedIn(cmd, arg);
                }
                return true;
        }
    }

    private boolean doPass(String pass) throws IOException {
        if (pendingUser == null) {
            send("503 Login with USER first");
            return true;
        }
        User u = UserDao.login(pendingUser, pass);
        if (u == null) {
            failedLogins++;
            TransferLogDao.log(null, "LOGIN", pendingUser, 0, "FAILED");
            pendingUser = null;
            if (failedLogins >= 3) {
                send("421 Too many failed logins");
                return false;
            }
            send("530 Login incorrect");
            return true;
        }
        File root = new File(u.getHomeDir());
        if (!root.exists()) root.mkdirs();
        if (!root.isDirectory()) {
            send("550 Home directory not available");
            return true;
        }
        user = u;
        homeRoot = root.getCanonicalFile();
        cwd = "/";
        TransferLogDao.log(u.getId(), "LOGIN", u.getUsername(), 0, "SUCCESS");
        send("230 Login successful");
        return true;
    }

    private void doLoggedIn(String cmd, String arg) throws IOException {
        switch (cmd) {
            case "TYPE":
                send("200 Type set to " + (arg.isEmpty() ? "I" : arg));
                break;
            case "MODE":
            case "STRU":
                send("200 OK");
                break;
            case "PWD":
            case "XPWD":
                send("257 \"" + cwd + "\" is current directory");
                break;
            case "CWD":
            case "XCWD": {
                String v = resolve(arg);
                if (real(v).isDirectory()) {
                    cwd = v;
                    send("250 Directory changed to " + v);
                } else {
                    send("550 Directory not found");
                }
                break;
            }
            case "CDUP":
            case "XCUP":
                cwd = resolve("..");
                send("250 Directory changed to " + cwd);
                break;
            case "PASV":
                doPasv();
                break;
            case "EPSV": {
                int port = data.openPassive(socket.getLocalAddress());
                send("229 Entering Extended Passive Mode (|||" + port + "|)");
                break;
            }
            case "PORT":
                doPort(arg);
                break;
            case "LIST":
                doList(arg, false);
                break;
            case "NLST":
                doList(arg, true);
                break;
            case "RETR":
                doRetr(arg);
                break;
            case "STOR":
                doStor(arg, false);
                break;
            case "APPE":
                doStor(arg, true);
                break;
            case "DELE":
                doDele(arg);
                break;
            case "MKD":
            case "XMKD":
                doMkd(arg);
                break;
            case "RMD":
            case "XRMD":
                doRmd(arg);
                break;
            case "RNFR": {
                String v = resolve(arg);
                if (real(v).exists() && !v.equals("/")) {
                    renameFrom = v;
                    send("350 Ready for RNTO");
                } else {
                    send("550 File not found");
                }
                break;
            }
            case "RNTO":
                doRnto(arg);
                break;
            case "SIZE": {
                File f = real(resolve(arg));
                if (f.isFile()) send("213 " + f.length()); else send("550 Not a regular file");
                break;
            }
            case "MDTM": {
                File f = real(resolve(arg));
                if (f.exists()) {
                    SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss");
                    fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                    send("213 " + fmt.format(new Date(f.lastModified())));
                } else {
                    send("550 File not found");
                }
                break;
            }
            case "REST":
                try {
                    restOffset = Math.max(0, Long.parseLong(arg));
                    send("350 Restarting at " + restOffset);
                } catch (NumberFormatException e) {
                    send("501 Invalid offset");
                }
                break;
            default:
                send("502 Command not implemented");
        }
    }

    // ------------------------------------------------------------------ duong dan

    /** Chuan hoa duong dan ao (khong cho thoat khoi "/"). */
    private String resolve(String arg) {
        String p = arg == null ? "" : arg.replace('\\', '/');
        String full = p.startsWith("/") ? p : cwd + "/" + p;
        LinkedList<String> parts = new LinkedList<>();
        for (String s : full.split("/")) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) {
                if (!parts.isEmpty()) parts.removeLast();
            } else {
                parts.add(s);
            }
        }
        return "/" + String.join("/", parts);
    }

    /** Doi duong dan ao sang File that, dam bao nam trong thu muc home cua user. */
    private File real(String virtual) throws IOException {
        File f = virtual.equals("/") ? homeRoot : new File(homeRoot, virtual.substring(1));
        String canon = f.getCanonicalPath();
        String root = homeRoot.getCanonicalPath();
        if (!(canon.equals(root) || canon.startsWith(root + File.separator))) {
            throw new SecurityException("Ngoai thu muc home");
        }
        return f;
    }

    private boolean requireWrite() throws IOException {
        if (!user.canWrite()) {
            send("550 Permission denied (read-only account)");
            return false;
        }
        return true;
    }

    private boolean requireRead() throws IOException {
        if (!user.canRead()) {
            send("550 Permission denied");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ ket noi du lieu

    private void doPasv() throws IOException {
        InetAddress local = socket.getLocalAddress();
        byte[] ip = local.getAddress();
        if (ip.length != 4) {
            if (local.isLoopbackAddress()) {
                local = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
                ip = local.getAddress();
            } else {
                send("425 Use EPSV for IPv6");
                return;
            }
        }
        int port = data.openPassive(local);
        send(String.format("227 Entering Passive Mode (%d,%d,%d,%d,%d,%d)",
                ip[0] & 0xFF, ip[1] & 0xFF, ip[2] & 0xFF, ip[3] & 0xFF, port / 256, port % 256));
    }

    private void doPort(String arg) throws IOException {
        String[] p = arg.split(",");
        if (p.length != 6) {
            send("501 Syntax error in PORT");
            return;
        }
        try {
            int[] n = new int[6];
            for (int i = 0; i < 6; i++) n[i] = Integer.parseInt(p[i].trim());
            InetAddress addr = InetAddress.getByAddress(
                    new byte[]{(byte) n[0], (byte) n[1], (byte) n[2], (byte) n[3]});
            if (!addr.equals(socket.getInetAddress())) {
                send("504 PORT address must match the control connection");
                return;
            }
            data.setActive(addr, n[4] * 256 + n[5]);
            send("200 PORT command successful");
        } catch (NumberFormatException e) {
            send("501 Syntax error in PORT");
        }
    }

    // ------------------------------------------------------------------ LIST / RETR / STOR

    private void doList(String arg, boolean namesOnly) throws IOException {
        if (!requireRead()) return;
        // bo qua cac option kieu "-a", "-la"
        String path = arg;
        while (path.startsWith("-")) {
            int sp = path.indexOf(' ');
            path = sp < 0 ? "" : path.substring(sp + 1).trim();
        }
        File target = real(resolve(path.isEmpty() ? "." : path));
        if (!target.exists()) {
            send("550 Path not found");
            return;
        }
        if (!data.isReady()) {
            send("425 Use PASV or PORT first");
            return;
        }
        File[] files = target.isDirectory() ? target.listFiles() : new File[]{target};
        if (files == null) files = new File[0];
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        send("150 Here comes the directory listing");
        boolean ok = false;
        try (Socket ds = data.open();
             Writer w = new OutputStreamWriter(ds.getOutputStream(), StandardCharsets.UTF_8)) {
            for (File f : files) {
                w.write(namesOnly ? f.getName() : formatListLine(f));
                w.write("\r\n");
            }
            w.flush();
            ok = true;
        } catch (IOException e) {
            // loi ket noi du lieu
        }
        send(ok ? "226 Directory send OK" : "426 Data connection error");
    }

    private String formatListLine(File f) {
        boolean dir = f.isDirectory();
        String perms = dir ? "drwxr-xr-x" : "-rw-r--r--";
        long size = dir ? 0 : f.length();
        Date d = new Date(f.lastModified());
        boolean recent = System.currentTimeMillis() - f.lastModified() < 180L * 24 * 3600 * 1000;
        String date = new SimpleDateFormat(recent ? "MMM dd HH:mm" : "MMM dd  yyyy", Locale.ENGLISH).format(d);
        return String.format("%s 1 ftp ftp %12d %s %s", perms, size, date, f.getName());
    }

    private void doRetr(String arg) throws IOException {
        if (!requireRead()) return;
        String v = resolve(arg);
        File f = real(v);
        if (!f.isFile()) {
            send("550 File not found");
            return;
        }
        if (!data.isReady()) {
            send("425 Use PASV or PORT first");
            return;
        }
        send("150 Opening data connection for " + f.getName());
        long sent = 0;
        boolean ok = false;
        try (Socket ds = data.open();
             RandomAccessFile raf = new RandomAccessFile(f, "r");
             OutputStream os = ds.getOutputStream()) {
            if (restOffset > 0) raf.seek(restOffset);
            byte[] buf = new byte[16384];
            int n;
            while ((n = raf.read(buf)) != -1) {
                os.write(buf, 0, n);
                sent += n;
            }
            os.flush();
            ok = true;
        } catch (IOException e) {
            // client huy hoac mat ket noi
        }
        restOffset = 0;
        TransferLogDao.log(user.getId(), "DOWNLOAD", v, sent, ok ? "SUCCESS" : "FAILED");
        send(ok ? "226 Transfer complete" : "426 Transfer aborted");
    }

    private void doStor(String arg, boolean append) throws IOException {
        if (!requireWrite()) return;
        if (arg.isEmpty()) {
            send("501 Missing file name");
            return;
        }
        String v = resolve(arg);
        File f = real(v);
        File parent = f.getParentFile();
        if (f.isDirectory() || parent == null || !parent.isDirectory()) {
            send("553 Cannot write to that path");
            return;
        }
        if (!data.isReady()) {
            send("425 Use PASV or PORT first");
            return;
        }
        send("150 Ready to receive " + f.getName());
        long received = 0;
        boolean ok = false;
        try (Socket ds = data.open();
             InputStream is = ds.getInputStream();
             RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            if (append) raf.seek(raf.length());
            else if (restOffset > 0) raf.seek(restOffset);
            else raf.setLength(0);
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) != -1) {
                raf.write(buf, 0, n);
                received += n;
            }
            ok = true;
        } catch (IOException e) {
            // mat ket noi giua chung
        }
        restOffset = 0;
        TransferLogDao.log(user.getId(), "UPLOAD", v, received, ok ? "SUCCESS" : "FAILED");
        send(ok ? "226 Transfer complete" : "426 Transfer aborted");
    }

    // ------------------------------------------------------------------ DELE / MKD / RMD / RNTO

    private void doDele(String arg) throws IOException {
        if (!requireWrite()) return;
        String v = resolve(arg);
        File f = real(v);
        if (!f.isFile()) {
            send("550 File not found");
            return;
        }
        long size = f.length();
        boolean ok = f.delete();
        TransferLogDao.log(user.getId(), "DELETE", v, size, ok ? "SUCCESS" : "FAILED");
        send(ok ? "250 File deleted" : "550 Delete failed");
    }

    private void doMkd(String arg) throws IOException {
        if (!requireWrite()) return;
        if (arg.isEmpty()) {
            send("501 Missing directory name");
            return;
        }
        String v = resolve(arg);
        File f = real(v);
        if (f.exists()) {
            send("550 Already exists");
            return;
        }
        boolean ok = f.mkdir();
        TransferLogDao.log(user.getId(), "MKD", v, 0, ok ? "SUCCESS" : "FAILED");
        send(ok ? "257 \"" + v + "\" created" : "550 Cannot create directory");
    }

    private void doRmd(String arg) throws IOException {
        if (!requireWrite()) return;
        String v = resolve(arg);
        File f = real(v);
        if (v.equals("/") || !f.isDirectory()) {
            send("550 Directory not found");
            return;
        }
        boolean ok = f.delete(); // chi xoa duoc thu muc rong
        TransferLogDao.log(user.getId(), "RMD", v, 0, ok ? "SUCCESS" : "FAILED");
        send(ok ? "250 Directory removed" : "550 Directory not empty or cannot be removed");
    }

    private void doRnto(String arg) throws IOException {
        if (!requireWrite()) return;
        if (renameFrom == null) {
            send("503 Send RNFR first");
            return;
        }
        String from = renameFrom;
        renameFrom = null;
        String toV = resolve(arg);
        File to = real(toV);
        File src = real(from);
        if (to.exists()) {
            send("550 Target already exists");
            return;
        }
        boolean ok = src.renameTo(to);
        TransferLogDao.log(user.getId(), "RENAME", from + " -> " + toV, 0, ok ? "SUCCESS" : "FAILED");
        send(ok ? "250 Renamed" : "550 Rename failed");
    }
}
