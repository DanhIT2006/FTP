package db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Truy cap bang saved_sites (Site Manager cua client). */
public class SiteDao {

    public static List<Site> findAll() throws SQLException {
        List<Site> list = new ArrayList<>();
        String sql = "SELECT * FROM saved_sites ORDER BY name, id";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Site(rs.getInt("id"), rs.getString("name"), rs.getString("host"),
                        rs.getInt("port"), rs.getString("username"), rs.getString("password")));
            }
        }
        return list;
    }

    public static void insert(Site s) throws SQLException {
        String sql = "INSERT INTO saved_sites (name, host, port, username, password) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getHost());
            ps.setInt(3, s.getPort());
            ps.setString(4, s.getUsername());
            ps.setString(5, s.getPassword());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) s.setId(rs.getInt("id"));
            }
        }
    }

    public static void update(Site s) throws SQLException {
        String sql = "UPDATE saved_sites SET name=?, host=?, port=?, username=?, password=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getHost());
            ps.setInt(3, s.getPort());
            ps.setString(4, s.getUsername());
            ps.setString(5, s.getPassword());
            ps.setInt(6, s.getId());
            ps.executeUpdate();
        }
    }

    public static void delete(int id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM saved_sites WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
