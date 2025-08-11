package com.contentgrid.appserver.content.impl.encryption.keys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.security.auth.Destroyable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(staticName = "adopt")
public final class KeyBytes implements Destroyable, Cloneable {
    private final byte[] keyBytes;

    /**
     * A list of copies that were made, so they can also be cleared
     * When this one gets destroyed
     */
    private final List<byte[]> copies = new ArrayList<>();

    @Getter
    private boolean destroyed = false;

    public static KeyBytes copy(byte[] bytes) {
        return adopt(bytes.clone());
    }

    public byte[] getKeyBytes() {
        if(isDestroyed()) {
            throw new IllegalStateException("Key has been destroyed");
        }
        return keyBytes;
    }

    public byte[] getKeyBytesCopy() {
        var copy = getKeyBytes().clone();
        copies.add(copy);
        return copy;
    }

    @Override
    public void destroy() {
        Arrays.fill(keyBytes, (byte)0);
        destroyed = true;
        for (var copy : copies) {
            Arrays.fill(copy, (byte) 0);
        }
        copies.clear();
    }

    @Override
    public KeyBytes clone() {
        // Create a whole independent copy
        return new KeyBytes(getKeyBytes().clone());
    }
}
