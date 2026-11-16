package db;

import java.sql.Timestamp;

public class TransferLog {
    private final int id;
    private final String username;
    private final String action;
    private final String filename;
    private final long fileSize;
    private final String status;
    private final Timestamp createdAt;

    public TransferLog(int id, String username, String action, String filename,
                       long fileSize, String status, Timestamp createdAt) {
        this.id = id;
        this.username = username;
        this.action = action;
        this.filename = filename;
        this.fileSize = fileSize;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getFilename() { return filename; }
    public long getFileSize() { return fileSize; }
    public String getStatus() { return status; }
    public Timestamp getCreatedAt() { return createdAt; }
}
