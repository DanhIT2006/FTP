package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {
    private static final String URL = "jdbc:postgresql://localhost:5432/ftpdb";
    private static final String USER = "postgres";
    private static final String PASS = "your_password"; // doi thanh mat khau PostgreSQL cua ban

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
