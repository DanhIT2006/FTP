package client;

import db.TransferLog;
import db.TransferLogDao;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.SQLException;
import java.text.SimpleDateFormat;

/** Xem bang transfer_logs (lich su dang nhap/upload/download/xoa... ghi boi server). */
public class TransferHistoryDialog extends JDialog {

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Time", "User", "Action", "File", "Size", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    public TransferHistoryDialog(JFrame owner) {
        super(owner, "Transfer history", false);
        setSize(900, 480);
        setLocationRelativeTo(owner);

        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(320);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> load());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(refresh);

        setLayout(new BorderLayout());
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        load();
    }

    private void load() {
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            model.setRowCount(0);
            for (TransferLog l : TransferLogDao.findRecent(500)) {
                model.addRow(new Object[]{
                        l.getCreatedAt() == null ? "" : fmt.format(l.getCreatedAt()),
                        l.getUsername() == null ? "-" : l.getUsername(),
                        l.getAction(),
                        l.getFilename(),
                        FileItem.formatSize(l.getFileSize()),
                        l.getStatus()});
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Khong doc duoc log: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
