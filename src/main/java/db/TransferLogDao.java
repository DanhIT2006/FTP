package db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class TransferLogDao {

    /** Ghi 1 dong log. userId co the null (vi du dang nhap sai). Khong nem loi de khong lam hong phien FTP. */
    public static void log(Integer userId, String action, String filename, long size, String status) {
        String sql = "INSERT INTO transfer_logs (user_id, action, filename, file_size, status) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, userId);
            ps.setString(2, cut(action, 20));
            ps.setString(3, cut(filename, 255));
            ps.setLong(4, size);
            ps.setString(5, cut(status, 20));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Khong ghi duoc log: " + e.getMessage());
        }
    }

    public static List<TransferLog> findRecent(int limit) throws SQLException {
        String sql = "SELECT l.id, u.username, l.action, l.filename, l.file_size, l.status, l.created_at "
                   + "FROM transfer_logs l LEFT JOIN users u ON u.id = l.user_id "
                   + "ORDER BY l.created_at DESC, l.id DESC LIMIT ?";
        List<TransferLog> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TransferLog(rs.getInt("id"), rs.getString("username"),
                            rs.getString("action"), rs.getString("filename"),
                            rs.getLong("file_size"), rs.getString("status"),
                            rs.getTimestamp("created_at")));
                }
            }
        }
        return list;
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
