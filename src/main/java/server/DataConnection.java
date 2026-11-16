package server;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class DataConnection {
    private static final int PORT_MIN = 50000;
    private static final int PORT_MAX = 50100;
    private static final int TIMEOUT_MS = 15000;

    private ServerSocket passiveServer;
    private InetAddress activeAddress;
    private int activePort;

    /** Mo cong passive, tra ve so cong. */
    public synchronized int openPassive(InetAddress bindAddress) throws IOException {
        close();
        for (int p = PORT_MIN; p <= PORT_MAX; p++) {
            try {
                passiveServer = new ServerSocket(p, 1, bindAddress);
                return p;
            } catch (BindException e) {
                // cong ban, thu cong tiep theo
            }
        }
        passiveServer = new ServerSocket(0, 1, bindAddress);
        return passiveServer.getLocalPort();
    }

    public synchronized void setActive(InetAddress address, int port) {
        close();
        this.activeAddress = address;
        this.activePort = port;
    }

    public synchronized boolean isReady() {
        return passiveServer != null || activeAddress != null;
    }

    /** Mo ket noi du lieu thuc su (accept hoac connect). */
    public Socket open() throws IOException {
        ServerSocket ss;
        InetAddress addr;
        int port;
        synchronized (this) {
            ss = passiveServer;
            addr = activeAddress;
            port = activePort;
            passiveServer = null;
            activeAddress = null;
        }
        if (ss != null) {
            try {
                ss.setSoTimeout(TIMEOUT_MS);
                Socket s = ss.accept();
                s.setSoTimeout(TIMEOUT_MS * 4);
                return s;
            } finally {
                try { ss.close(); } catch (IOException ignored) { }
            }
        }
        if (addr != null) {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(addr, port), TIMEOUT_MS);
            s.setSoTimeout(TIMEOUT_MS * 4);
            return s;
        }
        throw new IOException("Chua thiet lap ket noi du lieu (can PASV hoac PORT truoc)");
    }

    public synchronized void close() {
        if (passiveServer != null) {
            try { passiveServer.close(); } catch (IOException ignored) { }
            passiveServer = null;
        }
        activeAddress = null;
    }
}
