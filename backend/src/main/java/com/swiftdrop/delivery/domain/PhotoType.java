package com.swiftdrop.delivery.domain;

import java.util.Optional;

/**
 * Recognises proof photos by their leading bytes (D-12). The file name and the client's declared
 * content type are never trusted: a renamed file still fails.
 */
public final class PhotoType {

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";

    private static final byte[] JPEG_START = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_START = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PhotoType() {
    }

    /** The content type of a JPEG or PNG image, or empty for anything else. */
    public static Optional<String> detect(byte[] bytes) {
        if (startsWith(bytes, JPEG_START)) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, PNG_START)) {
            return Optional.of(PNG);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
