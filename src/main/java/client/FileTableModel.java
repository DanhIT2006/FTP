package client;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class FileTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Name", "Size", "Modified"};
    private List<FileItem> items = new ArrayList<>();

    public void setItems(List<FileItem> list) {
        items = new ArrayList<>(list);
        fireTableDataChanged();
    }

    public FileItem getItem(int row) {
        return items.get(row);
    }

    public boolean contains(String name) {
        for (FileItem it : items) {
            if (it.getName().equals(name) && !it.isParentLink()) return true;
        }
        return false;
    }

    @Override public int getRowCount() { return items.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int c) { return COLUMNS[c]; }

    @Override
    public Object getValueAt(int row, int col) {
        FileItem it = items.get(row);
        switch (col) {
            case 0: return it;
            case 1: return it.isDirectory() ? "" : FileItem.formatSize(it.getSize());
            default: return it.getModified();
        }
    }
}
