package com.contentgrid.appserver.contentstore.impl.encryption.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KeyBytesTest {
    @Test
    void destroyInitAdopted() {
        var bytes = new byte[] {1, 2};
        var keyBytes = KeyBytes.adopt(bytes);

        var bytesFromKey = keyBytes.getKeyBytes();
        assertThat(bytesFromKey).containsExactly(1, 2);

        keyBytes.destroy();
        // bytes from KeyBytes are destroyed
        assertThat(bytesFromKey).containsOnly(0);

        assertThatThrownBy(keyBytes::getKeyBytes)
                .hasMessage("Key has been destroyed");
        assertThatThrownBy(keyBytes::getKeyBytesCopy)
                .hasMessage("Key has been destroyed");

        // original is also destroyed
        assertThat(bytes).containsOnly(0);

    }

    @Test
    void destroyInitCopy() {
        var bytes = new byte[] {1, 2};
        var keyBytes = KeyBytes.copy(bytes);

        var bytesFromKey = keyBytes.getKeyBytes();
        assertThat(bytesFromKey).containsExactly(1, 2);

        keyBytes.destroy();
        // bytes from KeyBytes are destroyed
        assertThat(bytesFromKey).containsOnly(0);

        // original is untouched
        assertThat(bytes).containsExactly(1, 2);
    }

    @Test
    void bytesCopy() {
        var keyBytes = KeyBytes.adopt(new byte[] {1, 2});

        var keyBytesCopy = keyBytes.getKeyBytesCopy();

        assertThat(keyBytesCopy).containsExactly(1, 2);
        // modification of the copy
        keyBytesCopy[0] = 5;
        assertThat(keyBytesCopy).containsExactly(5, 2);
        // does not touch the original bytes
        assertThat(keyBytes.getKeyBytes()).containsExactly(1, 2);
        // and also not additional copies
        assertThat(keyBytes.getKeyBytesCopy()).containsExactly(1, 2);

        keyBytes.destroy();

        // copied bytes are also destroyed
        assertThat(keyBytesCopy).containsOnly(0);
    }

    @Test
    void fullClone() {
        var keyBytes = KeyBytes.adopt(new byte[] {1, 2});

        var keyBytesClone = keyBytes.clone();

        assertThat(keyBytesClone.getKeyBytes()).containsExactly(1, 2);

        keyBytes.destroy();
        assertThat(keyBytes.isDestroyed()).isTrue();

        // clone is unaffected
        assertThat(keyBytesClone.getKeyBytes()).containsExactly(1, 2);
    }

}