package com.contentgrid.appserver.contentstore.impl.encryption;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.contentstore.impl.encryption.keys.WrappingKeyId;
import org.junit.jupiter.api.Test;

class KeyUnwrappingFailedExceptionTest {
    private static final WrappingKeyId KEY_1 = WrappingKeyId.of("key-1");
    private static final WrappingKeyId KEY_2 = WrappingKeyId.of("key-2");
    private static final WrappingKeyId KEY_3 = WrappingKeyId.of("key-3");

    @Test
    void allFailedKeys() {
        var root = new KeyUnwrappingFailedException(KEY_1, new UnsupportedOperationException());
        root.addSuppressed(new KeyUnwrappingFailedException(KEY_2, new Throwable()));
        root.addSuppressed(new KeyUnwrappingFailedException(KEY_3, new RuntimeException()));

        assertThat(root.getAllFailedWrappingKeyIds())
                .containsExactlyInAnyOrder(KEY_1, KEY_2, KEY_3);
    }

    @Test
    void allFailedKeysRecursive() {
        var root = new KeyUnwrappingFailedException(KEY_1, new UnsupportedOperationException());
        var key2  = new KeyUnwrappingFailedException(KEY_2, new Throwable());
        root.addSuppressed(key2);
        key2.addSuppressed(new KeyUnwrappingFailedException(KEY_3, new RuntimeException()));

        assertThat(root.getAllFailedWrappingKeyIds())
                .containsExactlyInAnyOrder(KEY_1, KEY_2, KEY_3);
    }

    @Test
    void allFailedKeys_otherSuppressed() {
        var root = new KeyUnwrappingFailedException(KEY_1, new UnsupportedOperationException());
        root.addSuppressed(new RuntimeException("Some other exception"));
        root.addSuppressed(new KeyUnwrappingFailedException(KEY_2, new Throwable()));

        assertThat(root.getAllFailedWrappingKeyIds())
                .containsExactlyInAnyOrder(KEY_1, KEY_2);
    }
}