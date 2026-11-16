package server;

import db.Database;

import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FtpServer {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 2121; // 2121 de khong can quyen admin

        // Kiem tra ket noi database ngay luc khoi dong
        try (Connection c = Database.getConnection()) {
            System.out.println("Ket noi database OK");
        } catch (SQLException e) {
            System.err.println("KHONG ket noi duoc database: " + e.getMessage());
            System.err.println("Kiem tra lai URL/USER/PASS trong db/Database.java va PostgreSQL da chay chua.");
            return;
        }

        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("FTP Server dang chay tai cong " + port);
            while (true) {
                Socket socket = server.accept();
                pool.execute(new ClientHandler(socket));
            }
        }
    }
}
