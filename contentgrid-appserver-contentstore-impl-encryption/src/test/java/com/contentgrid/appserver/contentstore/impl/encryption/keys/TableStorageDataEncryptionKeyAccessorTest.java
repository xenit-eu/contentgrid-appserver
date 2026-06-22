package com.contentgrid.appserver.contentstore.impl.encryption.keys;

import com.contentgrid.appserver.contentstore.impl.encryption.testing.AbstractDataEncryptionKeyAccessorTest;
import lombok.Getter;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;

class TableStorageDataEncryptionKeyAccessorTest extends AbstractDataEncryptionKeyAccessorTest {
    @AutoClose
    private final CloseableDSLContext dslContext = DSL.using("jdbc:h2:mem:test", "sa", "sa");

    @Getter
    private final TableStorageDataEncryptionKeyAccessor dataEncryptionKeyAccessor = new TableStorageDataEncryptionKeyAccessor(dslContext);

    @Override
    @BeforeEach
    protected void setup() {
        TableStorageDataEncryptionKeyAccessor.setupTables(dslContext);

        super.setup();
    }

}