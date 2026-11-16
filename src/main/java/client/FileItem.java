package client;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 1 dong trong bang file (dung chung cho local va remote). */
public class FileItem {
    private final String name;
    private final boolean directory;
    private final long size;
    private final String modified;

    public FileItem(String name, boolean directory, long size, String modified) {
        this.name = name;
        this.directory = directory;
        this.size = size;
        this.modified = modified == null ? "" : modified;
    }

    public String getName() { return name; }
    public boolean isDirectory() { return directory; }
    public long getSize() { return size; }
    public String getModified() { return modified; }
    public boolean isParentLink() { return "..".equals(name); }

    /** Thu muc truoc, sau do theo ten (khong phan biet hoa thuong). */
    public static void sort(List<FileItem> items) {
        Collections.sort(items, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem a, FileItem b) {
                if (a.directory != b.directory) return a.directory ? -1 : 1;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
    }

    public static String formatSize(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + " B";
        double v = bytes / 1024.0;
        String[] units = {"KB", "MB", "GB", "TB"};
        int i = 0;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format("%.1f %s", v, units[i]);
    }
}
