package com.contentgrid.appserver.contentstore.impl.encryption.testing;

import com.contentgrid.appserver.contentstore.impl.encryption.keys.DataEncryptionKeyAccessor;
import lombok.Getter;

class InMemoryDataEncryptionKeyAccessorTest extends AbstractDataEncryptionKeyAccessorTest {
    @Getter
    private final DataEncryptionKeyAccessor dataEncryptionKeyAccessor = new InMemoryDataEncryptionKeyAccessor();

}