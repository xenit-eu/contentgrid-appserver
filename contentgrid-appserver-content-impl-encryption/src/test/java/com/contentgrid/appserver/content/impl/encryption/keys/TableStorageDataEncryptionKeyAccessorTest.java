package com.contentgrid.appserver.content.impl.encryption.keys;

import com.contentgrid.appserver.content.impl.encryption.testing.AbstractDataEncryptionKeyAccessorTest;
import lombok.Getter;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;

class TableStorageDataEncryptionKeyAccessorTest extends AbstractDataEncryptionKeyAccessorTest {
    @AutoClose
    private final CloseableDSLContext dslContext = DSL.using("jdbc:h2:mem:test", "sa", "sa");

    @Getter
    private final DataEncryptionKeyAccessor dataEncryptionKeyAccessor = new TableStorageDataEncryptionKeyAccessor(dslContext);

    @Override
    @BeforeEach
    protected void setup() {
        dslContext.createTable("_dek_storage")
                .column("content_id", SQLDataType.VARCHAR)
                .column("kek_label", SQLDataType.VARCHAR)
                .column("algorithm", SQLDataType.VARCHAR)
                .column("encrypted_dek", SQLDataType.BLOB)
                .column("iv", SQLDataType.BLOB)
                .execute();

        super.setup();
    }

}