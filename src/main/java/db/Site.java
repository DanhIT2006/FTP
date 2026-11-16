package db;

public class Site {
    private int id;
    private String name;
    private String host;
    private int port;
    private String username;
    private String password;

    public Site(int id, String name, String host, int port, String username, String password) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    @Override
    public String toString() {
        return (name == null || name.isEmpty()) ? host : name;
    }
}
