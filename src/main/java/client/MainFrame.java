package client;

import db.Site;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Giao dien chinh giong FileZilla: thanh ket noi, log, 2 khung Local / Remote, thanh tien trinh. */
public class MainFrame extends JFrame {

    private interface Task { void run() throws Exception; }

    private final FtpClient client = new FtpClient();
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ftp-worker");
        t.setDaemon(true);
        return t;
    });

    // thanh ket noi
    private final JTextField hostField = new JTextField("localhost", 12);
    private final JTextField userField = new JTextField("admin", 8);
    private final JPasswordField passField = new JPasswordField(8);
    private final JTextField portField = new JTextField("2121", 5);
    private final JButton connectBtn = new JButton("Connect");
    private final JButton disconnectBtn = new JButton("Disconnect");

    // log
    private final JTextArea logArea = new JTextArea(6, 80);

    // 2 khung file
    private final FileTableModel localModel = new FileTableModel();
    private final FileTableModel remoteModel = new FileTableModel();
    private final JTable localTable = new JTable(localModel);
    private final JTable remoteTable = new JTable(remoteModel);
    private final JTextField localPathField = new JTextField();
    private final JTextField remotePathField = new JTextField();
    private File localDir;                       // null = dang hien danh sach o dia
    private volatile String remotePath = "/";

    // thanh trang thai
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Not connected");
    private final JButton cancelBtn = new JButton("Cancel");

    private final List<JComponent> remoteOnly = new ArrayList<>();

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }

    public MainFrame() {
        super("MyFTP Client");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.cancel();
                System.exit(0);
            }
        });
        setSize(1200, 760);
        setLocationRelativeTo(null);

        client.setLogListener(this::appendLog);

        setJMenuBar(buildMenu());

        logArea.setEditable(false);
        logArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(100, 120));

        JPanel north = new JPanel(new BorderLayout());
        north.add(buildConnectionBar(), BorderLayout.NORTH);
        north.add(logScroll, BorderLayout.CENTER);

        JPanel localPane = buildPane("Local site:", localPathField, localTable, localModel,
                "Upload >>", this::uploadSelected, this::newFolderLocal, this::renameLocal,
                this::deleteLocal, () -> loadLocal(localDir), this::onLocalDouble,
                this::onLocalPathEnter, false);
        JPanel remotePane = buildPane("Remote site:", remotePathField, remoteTable, remoteModel,
                "<< Download", this::downloadSelected, this::newFolderRemote, this::renameRemote,
                this::deleteRemote, () -> runTask("Refreshing...", () -> loadRemote(null)),
                this::onRemoteDouble, this::onRemotePathEnter, true);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, localPane, remotePane);
        split.setResizeWeight(0.5);

        setLayout(new BorderLayout());
        add(north, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        loadLocal(new File(System.getProperty("user.home")));
        setConnected(false);
        appendLog("Status: Ready. Nhap thong tin va bam Connect, hoac mo Site Manager (Ctrl+S).");
    }

    // ================================================================== dung giao dien

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem sites = new JMenuItem("Site Manager...");
        sites.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        sites.addActionListener(e -> openSiteManager());
        JMenuItem disc = new JMenuItem("Disconnect");
        disc.addActionListener(e -> disconnect());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> {
            client.cancel();
            System.exit(0);
        });
        file.add(sites);
        file.add(disc);
        file.addSeparator();
        file.add(exit);

        JMenu view = new JMenu("View");
        JMenuItem hist = new JMenuItem("Transfer history (server log)...");
        hist.addActionListener(e -> new TransferHistoryDialog(this).setVisible(true));
        view.add(hist);

        bar.add(file);
        bar.add(view);
        return bar;
    }

    private JPanel buildConnectionBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        p.add(new JLabel("Host:"));
        p.add(hostField);
        p.add(new JLabel("Username:"));
        p.add(userField);
        p.add(new JLabel("Password:"));
        p.add(passField);
        p.add(new JLabel("Port:"));
        p.add(portField);
        p.add(connectBtn);
        p.add(disconnectBtn);
        JButton siteBtn = new JButton("Site Manager");
        p.add(siteBtn);

        connectBtn.addActionListener(e -> connectFromFields());
        disconnectBtn.addActionListener(e -> disconnect());
        siteBtn.addActionListener(e -> openSiteManager());
        hostField.addActionListener(e -> connectFromFields());
        userField.addActionListener(e -> connectFromFields());
        passField.addActionListener(e -> connectFromFields());
        portField.addActionListener(e -> connectFromFields());
        return p;
    }

    private JPanel buildStatusBar() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBorder(new EmptyBorder(4, 8, 4, 8));
        progressBar.setStringPainted(true);
        progressBar.setString("");
        cancelBtn.setEnabled(false);
        cancelBtn.addActionListener(e -> client.cancel());
        p.add(statusLabel, BorderLayout.WEST);
        p.add(progressBar, BorderLayout.CENTER);
        p.add(cancelBtn, BorderLayout.EAST);
        return p;
    }

    private JPanel buildPane(String title, JTextField pathField, JTable table, FileTableModel model,
                             String transferText, Runnable transfer, Runnable mkdir, Runnable rename,
                             Runnable delete, Runnable refresh, Consumer<FileItem> onDouble,
                             Runnable onPathEnter, boolean remote) {
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setBorder(new EmptyBorder(4, 6, 4, 6));
        top.add(new JLabel(title), BorderLayout.WEST);
        top.add(pathField, BorderLayout.CENTER);
        pathField.addActionListener(e -> onPathEnter.run());

        setupTable(table);
        JPopupMenu popup = new JPopupMenu();
        addMenuItem(popup, transferText.replace(">>", "").replace("<<", "").trim(), transfer);
        addMenuItem(popup, "New folder", mkdir);
        addMenuItem(popup, "Rename", rename);
        addMenuItem(popup, "Delete", delete);
        addMenuItem(popup, "Refresh", refresh);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int r = table.rowAtPoint(e.getPoint());
                    if (r >= 0) onDouble.accept(model.getItem(r));
                }
            }
            @Override public void mousePressed(MouseEvent e) { maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            private void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int r = table.rowAtPoint(e.getPoint());
                if (r >= 0 && !table.isRowSelected(r)) table.setRowSelectionInterval(r, r);
                popup.show(table, e.getX(), e.getY());
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton b1 = button(transferText, transfer);
        JButton b2 = button("New folder", mkdir);
        JButton b3 = button("Rename", rename);
        JButton b4 = button("Delete", delete);
        JButton b5 = button("Refresh", refresh);
        for (JButton b : new JButton[]{b1, b2, b3, b4, b5}) buttons.add(b);
        if (remote) {
            Collections.addAll(remoteOnly, b1, b2, b3, b4, b5);
        } else {
            remoteOnly.add(b1); // Upload chi dung duoc khi da ket noi
        }

        JPanel pane = new JPanel(new BorderLayout());
        pane.add(top, BorderLayout.NORTH);
        pane.add(new JScrollPane(table), BorderLayout.CENTER);
        pane.add(buttons, BorderLayout.SOUTH);
        return pane;
    }

    private void setupTable(JTable t) {
        t.setShowGrid(false);
        t.setRowHeight(22);
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        t.getColumnModel().getColumn(0).setPreferredWidth(280);
        t.getColumnModel().getColumn(1).setPreferredWidth(80);
        t.getColumnModel().getColumn(2).setPreferredWidth(140);
        t.getColumnModel().getColumn(0).setCellRenderer(new NameRenderer());
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        t.getColumnModel().getColumn(1).setCellRenderer(right);
    }

    private static class NameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            FileItem it = (FileItem) value;
            super.getTableCellRendererComponent(table, it.getName(), isSelected, hasFocus, row, column);
            setIcon(UIManager.getIcon(it.isDirectory() ? "FileView.directoryIcon" : "FileView.fileIcon"));
            return this;
        }
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void addMenuItem(JPopupMenu menu, String text, Runnable action) {
        JMenuItem mi = new JMenuItem(text);
        mi.addActionListener(e -> action.run());
        menu.add(mi);
    }

    // ================================================================== tien ich chung

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void setConnected(boolean connected) {
        disconnectBtn.setEnabled(connected);
        for (JComponent c : remoteOnly) c.setEnabled(connected);
        statusLabel.setText(connected ? "Connected" : "Not connected");
    }

    private void setBusy(boolean busy, String text) {
        statusLabel.setText(text);
        cancelBtn.setEnabled(busy);
        if (!busy) {
            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressBar.setString("");
        }
    }

    private void info(String msg) {
        JOptionPane.showMessageDialog(this, msg, "MyFTP", JOptionPane.INFORMATION_MESSAGE);
    }

    private boolean confirm(String msg) {
        return JOptionPane.showConfirmDialog(this, msg, "Confirm", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
    }

    /** Chay tac vu mang tren thread rieng (tuan tu), cap nhat trang thai va bao loi. */
    private void runTask(String status, Task task) {
        exec.submit(() -> {
            client.resetCancel();
            SwingUtilities.invokeLater(() -> setBusy(true, status));
            try {
                task.run();
            } catch (FtpClient.CancelledException ex) {
                appendLog("Status: Transfer cancelled");
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                appendLog("Error: " + msg);
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE));
            } finally {
                SwingUtilities.invokeLater(() ->
                        setBusy(false, client.isConnected() ? "Connected" : "Not connected"));
            }
        });
    }

    /** Chay task roi luon tai lai danh sach remote (ke ca khi task bi loi/huy). */
    private void withRemoteRefresh(Task t) throws Exception {
        Exception err = null;
        try {
            t.run();
        } catch (Exception e) {
            err = e;
        }
        try {
            loadRemote(null);
        } catch (Exception ignored) { }
        if (err != null) throw err;
    }

    private FtpClient.ProgressListener progressListener(String name) {
        return (done, total) -> SwingUtilities.invokeLater(() -> {
            if (total > 0) {
                int pct = (int) Math.min(100, done * 100 / total);
                progressBar.setIndeterminate(false);
                progressBar.setValue(pct);
                progressBar.setString(name + " - " + pct + "% (" + FileItem.formatSize(done)
                        + " / " + FileItem.formatSize(total) + ")");
            } else {
                progressBar.setIndeterminate(true);
                progressBar.setString(name + " - " + FileItem.formatSize(done));
            }
        });
    }

    private List<FileItem> selected(JTable t, FileTableModel m) {
        List<FileItem> out = new ArrayList<>();
        for (int r : t.getSelectedRows()) {
            FileItem it = m.getItem(r);
            if (!it.isParentLink()) out.add(it);
        }
        return out;
    }

    // ================================================================== ket noi

    private void connectFromFields() {
        final String host = hostField.getText().trim();
        final String user = userField.getText().trim();
        final String pass = new String(passField.getPassword());
        final int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            info("Port phai la so.");
            return;
        }
        if (host.isEmpty()) {
            info("Hay nhap Host.");
            return;
        }
        runTask("Connecting...", () -> {
            try {
                client.connect(host, port);
                client.login(user, pass);
            } catch (IOException e) {
                client.disconnect();
                SwingUtilities.invokeLater(() -> setConnected(false));
                throw e;
            }
            SwingUtilities.invokeLater(() -> setConnected(true));
            loadRemote(null);
        });
    }

    private void disconnect() {
        runTask("Disconnecting...", () -> {
            client.quit();
            SwingUtilities.invokeLater(() -> {
                setConnected(false);
                remoteModel.setItems(new ArrayList<FileItem>());
                remotePathField.setText("");
                remotePath = "/";
            });
        });
    }

    private void openSiteManager() {
        SiteManagerDialog dlg = new SiteManagerDialog(this);
        dlg.setVisible(true);
        Site s = dlg.getSelectedSite();
        if (s != null) {
            hostField.setText(s.getHost());
            portField.setText(String.valueOf(s.getPort()));
            userField.setText(s.getUsername() == null ? "" : s.getUsername());
            passField.setText(s.getPassword() == null ? "" : s.getPassword());
            connectFromFields();
        }
    }

    // ================================================================== khung REMOTE

    /** Chay tren thread worker. cdTo = null: giu nguyen thu muc, chi tai lai danh sach. */
    private void loadRemote(String cdTo) throws IOException {
        if (cdTo != null) client.cwd(cdTo);
        final String p = client.pwd();
        List<FileItem> items = client.list(null);
        FileItem.sort(items);
        items.add(0, new FileItem("..", true, -1, ""));
        SwingUtilities.invokeLater(() -> {
            remotePath = p;
            remotePathField.setText(p);
            remoteModel.setItems(items);
        });
    }

    private void onRemotePathEnter() {
        final String p = remotePathField.getText().trim();
        if (p.isEmpty() || !client.isConnected()) return;
        runTask("Opening directory...", () -> loadRemote(p));
    }

    private void onRemoteDouble(FileItem it) {
        if (!client.isConnected()) return;
        if (it.isDirectory()) {
            final String target = it.isParentLink() ? ".." : FtpClient.join(remotePath, it.getName());
            runTask("Opening directory...", () -> loadRemote(target));
        } else {
            downloadItems(Collections.singletonList(it));
        }
    }

    private void downloadSelected() {
        downloadItems(selected(remoteTable, remoteModel));
    }

    private void downloadItems(List<FileItem> items) {
        if (items.isEmpty()) return;
        if (!client.isConnected()) {
            info("Chua ket noi toi server.");
            return;
        }
        final File dir = localDir;
        if (dir == null) {
            info("Hay chon mot thu muc local de luu file.");
            return;
        }
        final String rdir = remotePath;
        List<String> clash = new ArrayList<>();
        for (FileItem it : items) if (localModel.contains(it.getName())) clash.add(it.getName());
        if (!clash.isEmpty() && !confirm("Cac muc sau da ton tai o local:\n" + String.join("\n", clash)
                + "\nGhi de?")) return;

        runTask("Downloading...", () -> {
            try {
                for (FileItem it : items) {
                    downloadPath(FtpClient.join(rdir, it.getName()), new File(dir, it.getName()), it.isDirectory());
                }
            } finally {
                SwingUtilities.invokeLater(() -> loadLocal(localDir));
            }
        });
    }

    private void downloadPath(String remote, File local, boolean isDir) throws IOException {
        if (isDir) {
            local.mkdirs();
            for (FileItem c : client.list(remote)) {
                downloadPath(FtpClient.join(remote, c.getName()), new File(local, c.getName()), c.isDirectory());
            }
        } else {
            client.download(remote, local, progressListener(local.getName()));
        }
    }

    private void newFolderRemote() {
        if (!client.isConnected()) return;
        String name = JOptionPane.showInputDialog(this, "Ten thu muc moi:");
        if (name == null || name.trim().isEmpty()) return;
        final String path = FtpClient.join(remotePath, name.trim());
        runTask("Creating folder...", () -> withRemoteRefresh(() -> client.mkd(path)));
    }

    private void renameRemote() {
        List<FileItem> sel = selected(remoteTable, remoteModel);
        if (sel.size() != 1) {
            info("Hay chon dung 1 muc de doi ten.");
            return;
        }
        String oldName = sel.get(0).getName();
        String newName = JOptionPane.showInputDialog(this, "Ten moi:", oldName);
        if (newName == null || newName.trim().isEmpty() || newName.trim().equals(oldName)) return;
        final String from = FtpClient.join(remotePath, oldName);
        final String to = FtpClient.join(remotePath, newName.trim());
        runTask("Renaming...", () -> withRemoteRefresh(() -> client.rename(from, to)));
    }

    private void deleteRemote() {
        List<FileItem> sel = selected(remoteTable, remoteModel);
        if (sel.isEmpty()) return;
        if (!confirm("Xoa " + sel.size() + " muc da chon tren server?")) return;
        final String rdir = remotePath;
        runTask("Deleting...", () -> withRemoteRefresh(() -> {
            for (FileItem it : sel) {
                client.deleteRecursive(FtpClient.join(rdir, it.getName()), it.isDirectory());
            }
        }));
    }

    // ================================================================== khung LOCAL

    private void loadLocal(File dir) {
        List<FileItem> items = new ArrayList<>();
        if (dir == null) {
            for (File r : File.listRoots()) items.add(new FileItem(r.getPath(), true, -1, ""));
            localPathField.setText("");
        } else {
            File[] files = dir.listFiles();
            if (files == null) {
                info("Khong mo duoc thu muc: " + dir.getAbsolutePath());
                return;
            }
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            List<FileItem> list = new ArrayList<>();
            for (File f : files) {
                list.add(new FileItem(f.getName(), f.isDirectory(),
                        f.isDirectory() ? -1 : f.length(), fmt.format(new Date(f.lastModified()))));
            }
            FileItem.sort(list);
            items.add(new FileItem("..", true, -1, ""));
            items.addAll(list);
            localPathField.setText(dir.getAbsolutePath());
        }
        localDir = dir;
        localModel.setItems(items);
    }

    private void onLocalPathEnter() {
        File f = new File(localPathField.getText().trim());
        if (f.isDirectory()) loadLocal(f);
        else info("Thu muc khong ton tai.");
    }

    private void onLocalDouble(FileItem it) {
        if (it.isDirectory()) {
            if (it.isParentLink()) {
                if (localDir != null) loadLocal(localDir.getParentFile()); // null -> hien o dia
            } else {
                loadLocal(localDir == null ? new File(it.getName()) : new File(localDir, it.getName()));
            }
        } else {
            uploadItems(Collections.singletonList(it));
        }
    }

    private void uploadSelected() {
        uploadItems(selected(localTable, localModel));
    }

    private void uploadItems(List<FileItem> items) {
        if (items.isEmpty()) return;
        if (!client.isConnected()) {
            info("Chua ket noi toi server.");
            return;
        }
        final File dir = localDir;
        if (dir == null) return;
        final String rdir = remotePath;
        List<String> clash = new ArrayList<>();
        for (FileItem it : items) if (remoteModel.contains(it.getName())) clash.add(it.getName());
        if (!clash.isEmpty() && !confirm("Cac muc sau da ton tai tren server:\n" + String.join("\n", clash)
                + "\nGhi de?")) return;

        runTask("Uploading...", () -> withRemoteRefresh(() -> {
            for (FileItem it : items) {
                uploadPath(new File(dir, it.getName()), FtpClient.join(rdir, it.getName()));
            }
        }));
    }

    private void uploadPath(File f, String remote) throws IOException {
        if (f.isDirectory()) {
            try {
                client.mkd(remote);
            } catch (IOException ignored) {
                // co the da ton tai
            }
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) uploadPath(k, FtpClient.join(remote, k.getName()));
            }
        } else {
            client.upload(f, remote, progressListener(f.getName()));
        }
    }

    private void newFolderLocal() {
        if (localDir == null) return;
        String name = JOptionPane.showInputDialog(this, "Ten thu muc moi:");
        if (name == null || name.trim().isEmpty()) return;
        if (!new File(localDir, name.trim()).mkdir()) info("Khong tao duoc thu muc.");
        loadLocal(localDir);
    }

    private void renameLocal() {
        List<FileItem> sel = selected(localTable, localModel);
        if (sel.size() != 1 || localDir == null) {
            info("Hay chon dung 1 muc de doi ten.");
            return;
        }
        String oldName = sel.get(0).getName();
        String newName = JOptionPane.showInputDialog(this, "Ten moi:", oldName);
        if (newName == null || newName.trim().isEmpty() || newName.trim().equals(oldName)) return;
        if (!new File(localDir, oldName).renameTo(new File(localDir, newName.trim()))) {
            info("Doi ten that bai.");
        }
        loadLocal(localDir);
    }

    private void deleteLocal() {
        List<FileItem> sel = selected(localTable, localModel);
        if (sel.isEmpty() || localDir == null) return;
        if (!confirm("Xoa " + sel.size() + " muc da chon o may local?")) return;
        for (FileItem it : sel) deleteRecursive(new File(localDir, it.getName()));
        loadLocal(localDir);
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }
}
