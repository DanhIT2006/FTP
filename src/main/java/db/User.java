package db;

public class User {
    private final int id;
    private final String username;
    private final String homeDir;
    private final boolean canRead;
    private final boolean canWrite;

    public User(int id, String username, String homeDir, boolean canRead, boolean canWrite) {
        this.id = id;
        this.username = username;
        this.homeDir = homeDir;
        this.canRead = canRead;
        this.canWrite = canWrite;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getHomeDir() { return homeDir; }
    public boolean canRead() { return canRead; }
    public boolean canWrite() { return canWrite; }
}
