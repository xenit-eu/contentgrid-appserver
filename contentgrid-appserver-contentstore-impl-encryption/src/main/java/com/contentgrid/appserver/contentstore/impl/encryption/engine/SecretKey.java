package com.contentgrid.appserver.contentstore.impl.encryption.engine;

import com.contentgrid.appserver.contentstore.impl.encryption.keys.KeyBytes;

import javax.security.auth.Destroyable;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@RequiredArgsConstructor
class SecretKey implements javax.crypto.SecretKey {

    // this delegation is primary reason for SecretKey class vs use of SecretKeySpec
    @Delegate(types = Destroyable.class)
    private final KeyBytes keyBytes;

    private final String algorithm;

    @Override
    public String getAlgorithm() {
        return this.algorithm;
    }

    @Override
    public String getFormat() {
        return "RAW";
    }

    @Override
    public byte[] getEncoded() {
        // This one needs to be a copy, just as Java SecretKeySpec clones
        // Returned array may be manipulated/cleared within (AES) Cipher impl
        // We don't want to have it destroy our KeyBytes copy
        return keyBytes.getKeyBytesCopy();
    }
}
