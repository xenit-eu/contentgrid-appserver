package com.contentgrid.appserver.content.impl.encryption.testing;

import com.contentgrid.appserver.content.impl.encryption.keys.DataEncryptionKeyAccessor;
import lombok.Getter;

class InMemoryDataEncryptionKeyAccessorTest extends AbstractDataEncryptionKeyAccessorTest {
    @Getter
    private final DataEncryptionKeyAccessor dataEncryptionKeyAccessor = new InMemoryDataEncryptionKeyAccessor();

}