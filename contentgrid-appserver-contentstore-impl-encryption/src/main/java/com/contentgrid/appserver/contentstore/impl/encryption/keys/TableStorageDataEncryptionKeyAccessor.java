package com.contentgrid.appserver.contentstore.impl.encryption.keys;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.DataEncryptionAlgorithm;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;

@RequiredArgsConstructor
public class TableStorageDataEncryptionKeyAccessor implements DataEncryptionKeyAccessor {
    private final DSLContext dslContext;

    private static final String TABLE_NAME = "_dek_storage";
    private static final org.jooq.Table<Record> DEK_STORAGE = table(name(TABLE_NAME));
    private static final Field<String> CONTENT_ID = field(name(TABLE_NAME, "content_id"), String.class);
    private static final Field<String> KEK_LABEL = field(name(TABLE_NAME, "kek_label"), String.class);
    private static final Field<byte[]> ENCRYPTED_DEK = field(name(TABLE_NAME, "encrypted_dek"), byte[].class);
    private static final Field<String> ALGORITHM = field(name(TABLE_NAME, "algorithm"), String.class);
    private static final Field<byte[]> INITIALIZATION_VECTOR = field(name(TABLE_NAME, "iv"), byte[].class);

    @Override
    public List<StoredDataEncryptionKey> findAllKeys(ContentReference contentReference) {
        return dslContext.select(DEK_STORAGE.asterisk())
                .from(DEK_STORAGE)
                .where(CONTENT_ID.eq(contentReference.getValue()))
                .fetch(dekRecord -> new StoredDataEncryptionKey(
                        DataEncryptionAlgorithm.of(dekRecord.get(ALGORITHM)),
                        WrappingKeyId.of(dekRecord.get(KEK_LABEL)),
                        KeyBytes.adopt(dekRecord.get(ENCRYPTED_DEK)),
                        dekRecord.get(INITIALIZATION_VECTOR)
                ));
    }

    @Override
    public void addKeys(ContentReference contentReference, Set<StoredDataEncryptionKey> dataEncryptionKeys) {
        var toInsert = dataEncryptionKeys.stream()
                .map(dek -> {
                    var dekRecord = dslContext.newRecord(
                            CONTENT_ID,
                            ALGORITHM,
                            KEK_LABEL,
                            ENCRYPTED_DEK,
                            INITIALIZATION_VECTOR
                    );
                    dekRecord.set(CONTENT_ID, contentReference.getValue());
                    dekRecord.set(ALGORITHM, dek.getDataEncryptionAlgorithm().getValue());
                    dekRecord.set(KEK_LABEL, dek.getWrappingKeyId().getValue());
                    dekRecord.set(ENCRYPTED_DEK, dek.getEncryptedKeyData().getKeyBytes());
                    dekRecord.set(INITIALIZATION_VECTOR, dek.getInitializationVector());
                    return dekRecord;
                })
                .toList();

        dslContext.insertInto(DEK_STORAGE,
                        CONTENT_ID,
                        ALGORITHM,
                        KEK_LABEL,
                        ENCRYPTED_DEK,
                        INITIALIZATION_VECTOR
                )
                .valuesOfRecords(toInsert)
                .execute();
    }

    @Override
    public void removeKey(ContentReference contentReference, WrappingKeyId wrappingKeyId) {
        dslContext.deleteFrom(DEK_STORAGE)
                .where(
                        CONTENT_ID.eq(contentReference.getValue()),
                        KEK_LABEL.eq(wrappingKeyId.getValue())
                )
                .execute();
    }

    @Override
    public void clearKeys(ContentReference contentReference) {
        dslContext.deleteFrom(DEK_STORAGE)
                .where(CONTENT_ID.eq(contentReference.getValue()))
                .execute();
    }
}
