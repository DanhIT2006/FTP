package util;

import db.UserDao;
import java.io.File;
import java.util.Scanner;


public class CreateUser {
    public static void main(String[] args) {
        String username, password, homeDir;
        if (args.length >= 3) {
            username = args[0];
            password = args[1];
            homeDir = args[2];
        } else {
            Scanner sc = new Scanner(System.in);
            System.out.print("Username: ");
            username = sc.nextLine().trim();
            System.out.print("Password: ");
            password = sc.nextLine();
            System.out.print("Thu muc home (vd C:/ftp_root/admin): ");
            homeDir = sc.nextLine().trim();
        }
        if (username.isEmpty() || password.isEmpty() || homeDir.isEmpty()) {
            System.out.println("Thieu thong tin, huy.");
            return;
        }
        File home = new File(homeDir);
        if (!home.exists() && !home.mkdirs()) {
            System.out.println("Khong tao duoc thu muc " + homeDir);
            return;
        }
        boolean ok = UserDao.createUser(username, password, homeDir, true, true);
        System.out.println(ok ? "Da tao user '" + username + "'." : "Tao user that bai (xem loi o tren).");
    }
}
