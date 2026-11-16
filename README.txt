MYFTP - FTP Server + Client (giong FileZilla) - Java + PostgreSQL
=================================================================

CAU TRUC
  sql/create_tables.sql        Script tao 3 bang (users, transfer_logs, saved_sites)
  src/main/java/db/            Ket noi PostgreSQL + DAO (UserDao, TransferLogDao, SiteDao)
  src/main/java/server/        FtpServer, ClientHandler, DataConnection
  src/main/java/client/        MainFrame (GUI), FtpClient, SiteManagerDialog, TransferHistoryDialog
  src/main/java/util/          CreateUser (tao tai khoan), HashTest

CAI DAT
  1. PostgreSQL: tao database ftpdb, chay sql/create_tables.sql (Query Tool).
  2. Sua mat khau PostgreSQL trong src/main/java/db/Database.java (bien PASS).
  3. Mo project bang VS Code (Extension Pack for Java + Maven for Java),
     chuot phai pom.xml -> Reload project de tai thu vien.
     Yeu cau JDK 11 tro len.

CHAY
  1. Tao user:   chay util/CreateUser.java (nhap username, password, thu muc home)
                 vd: admin / 123456 / C:/ftp_root/admin
  2. Chay server: server/FtpServer.java  (cong 2121)
  3. Chay client: client/MainFrame.java
     Host: localhost, Port: 2121, Username/Password vua tao -> Connect.
  (Co the dung FileZilla that de test server: host localhost, port 2121.)

TINH NANG
  Server : USER/PASS (BCrypt tu DB), PWD, CWD, CDUP, LIST, NLST, PASV, EPSV, PORT,
           RETR, STOR, APPE, REST (resume), DELE, MKD, RMD, RNFR/RNTO, SIZE, MDTM,
           phan quyen can_read / can_write, moi user bi nhot trong thu muc home,
           khoa sau 3 lan sai mat khau, ghi moi thao tac vao bang transfer_logs.
  Client : 2 khung Local / Remote, double-click de vao thu muc hoac upload/download,
           upload/download ca thu muc, xoa de quy, doi ten, tao thu muc,
           thanh tien trinh + nut Cancel, khung log lenh, Site Manager (bang saved_sites),
           View > Transfer history xem bang transfer_logs.

LUU Y
  - Mat khau trong saved_sites dang luu dang van ban thuong (don gian cho bai tap).
  - Passive mode dung cong 50000-50100.
