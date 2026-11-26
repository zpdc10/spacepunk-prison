package dev.spacepunk.prison.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The pack zip in memory plus its SHA-1, which the client checks after downloading. */
record PackFile(byte[] bytes, String sha1, String source) {

    static PackFile read(InputStream in, String source) throws IOException {
        try (in) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) throw new IOException("Pack " + source + " is empty");
            return new PackFile(bytes, sha1(bytes), source);
        }
    }

    static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
