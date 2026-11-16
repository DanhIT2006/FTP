package db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;

public class UserDao {

    /** Tra ve User neu dung username/password, nguoc lai tra ve null. */
    public static User login(String username, String password) {
        if (username == null || password == null) return null;
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && BCrypt.checkpw(password, rs.getString("password_hash"))) {
                    return new User(rs.getInt("id"), username, rs.getString("home_dir"),
                            rs.getBoolean("can_read"), rs.getBoolean("can_write"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Loi database khi login: " + e.getMessage());
        }
        return null;
    }

    /** Tao user moi (mat khau duoc hash bang BCrypt). */
    public static boolean createUser(String username, String password, String homeDir,
                                     boolean canRead, boolean canWrite) {
        String sql = "INSERT INTO users (username, password_hash, home_dir, can_read, can_write) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
            ps.setString(3, homeDir);
            ps.setBoolean(4, canRead);
            ps.setBoolean(5, canWrite);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Khong tao duoc user: " + e.getMessage());
            return false;
        }
    }
}
