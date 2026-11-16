package client;

import db.Site;
import db.SiteDao;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.SQLException;
import java.util.List;

/** Quan ly danh sach server da luu (bang saved_sites), giong Site Manager cua FileZilla. */
public class SiteManagerDialog extends JDialog {

    private final DefaultListModel<Site> model = new DefaultListModel<>();
    private final JList<Site> list = new JList<>(model);
    private final JTextField nameF = new JTextField(18);
    private final JTextField hostF = new JTextField(18);
    private final JTextField portF = new JTextField("2121", 18);
    private final JTextField userF = new JTextField(18);
    private final JPasswordField passF = new JPasswordField(18);

    private Site editing;        // site dang sua (null = site moi)
    private Site selectedSite;   // ket qua khi bam Connect

    public SiteManagerDialog(JFrame owner) {
        super(owner, "Site Manager", true);
        setSize(640, 360);
        setLocationRelativeTo(owner);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) fillForm(list.getSelectedValue());
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(180, 100));

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setBorder(new EmptyBorder(8, 8, 8, 4));
        left.add(new JLabel("Saved sites:"), BorderLayout.NORTH);
        left.add(listScroll, BorderLayout.CENTER);
        JButton newBtn = new JButton("New site");
        newBtn.addActionListener(e -> newSite());
        left.add(newBtn, BorderLayout.SOUTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 4, 8, 8));
        addRow(form, 0, "Name:", nameF);
        addRow(form, 1, "Host:", hostF);
        addRow(form, 2, "Port:", portF);
        addRow(form, 3, "Username:", userF);
        addRow(form, 4, "Password:", passF);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        JButton saveBtn = new JButton("Save");
        JButton delBtn = new JButton("Delete");
        JButton connectBtn = new JButton("Connect");
        JButton closeBtn = new JButton("Close");
        saveBtn.addActionListener(e -> save());
        delBtn.addActionListener(e -> delete());
        connectBtn.addActionListener(e -> connect());
        closeBtn.addActionListener(e -> dispose());
        buttons.add(saveBtn);
        buttons.add(delBtn);
        buttons.add(connectBtn);
        buttons.add(closeBtn);

        setLayout(new BorderLayout());
        add(left, BorderLayout.WEST);
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        reload(null);
    }

    public Site getSelectedSite() {
        return selectedSite;
    }

    private void addRow(JPanel p, int row, String label, java.awt.Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        p.add(new JLabel(label), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        p.add(field, c);
    }

    private void fillForm(Site s) {
        editing = s;
        nameF.setText(s.getName());
        hostF.setText(s.getHost());
        portF.setText(String.valueOf(s.getPort()));
        userF.setText(s.getUsername() == null ? "" : s.getUsername());
        passF.setText(s.getPassword() == null ? "" : s.getPassword());
    }

    private void newSite() {
        editing = null;
        list.clearSelection();
        nameF.setText("New site");
        hostF.setText("");
        portF.setText("2121");
        userF.setText("");
        passF.setText("");
        nameF.requestFocus();
    }

    /** Doc form thanh Site; tra ve null neu du lieu khong hop le. */
    private Site fromForm() {
        String host = hostF.getText().trim();
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Host khong duoc de trong.");
            return null;
        }
        int port;
        try {
            port = Integer.parseInt(portF.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port phai la so.");
            return null;
        }
        String name = nameF.getText().trim();
        if (name.isEmpty()) name = host;
        return new Site(editing == null ? 0 : editing.getId(), name, host, port,
                userF.getText().trim(), new String(passF.getPassword()));
    }

    private void save() {
        Site s = fromForm();
        if (s == null) return;
        try {
            if (editing == null) SiteDao.insert(s); else SiteDao.update(s);
            reload(s.getId());
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Loi database: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void delete() {
        if (editing == null) return;
        if (JOptionPane.showConfirmDialog(this, "Xoa site '" + editing + "'?", "Confirm",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            SiteDao.delete(editing.getId());
            editing = null;
            reload(null);
            newSite();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Loi database: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void connect() {
        Site s = fromForm();
        if (s == null) return;
        selectedSite = s;
        dispose();
    }

    private void reload(Integer selectId) {
        try {
            List<Site> sites = SiteDao.findAll();
            model.clear();
            Site toSelect = null;
            for (Site s : sites) {
                model.addElement(s);
                if (selectId != null && s.getId() == selectId) toSelect = s;
            }
            if (toSelect != null) list.setSelectedValue(toSelect, true);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Khong doc duoc danh sach site: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
