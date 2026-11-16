package util;

import org.mindrot.jbcrypt.BCrypt;

/** In ra hash BCrypt cua "123456" (chi de tham khao, nen dung CreateUser). */
public class HashTest {
    public static void main(String[] args) {
        System.out.println(BCrypt.hashpw("123456", BCrypt.gensalt()));
    }
}
