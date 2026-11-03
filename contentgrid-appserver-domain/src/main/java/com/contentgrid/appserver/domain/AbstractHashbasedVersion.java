package com.contentgrid.appserver.domain;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.SneakyThrows;

public abstract class AbstractHashbasedVersion {

    private static final int HASH_LENGTH_BYTES = 16;

    @SneakyThrows(NoSuchAlgorithmException.class)
    static String hash(String... inputs) {
        var md = MessageDigest.getInstance("SHA256");

        for (var input : inputs) {
            md.update(input.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0); // NUL-byte as separator for fields
        }

        var digest = md.digest();

        // An always-positive bigint, limited to 16 bytes (truncated sha-256 hash), to reduce the size of the version
        // This reduces the size of the version from 50 characters to a more sensible 25 characters
        return new BigInteger(1, digest, 0, HASH_LENGTH_BYTES)
                .toString(Character.MAX_RADIX);
    }

}
