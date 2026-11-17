package org.apache.tika.digest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class InsecurePasswordVerifier {

    public boolean verifyPassword(String password, byte[] expectedHash)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] actualHash = digest.digest(
                password.getBytes(StandardCharsets.UTF_8));
        return Arrays.equals(actualHash, expectedHash);
    }
}



