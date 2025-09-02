package com.contentgrid.appserver.content.impl.encryption.keys;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.impl.encryption.engine.DataEncryptionAlgorithm;
import java.util.Set;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TableStorageDataEncryptionKeyAccessorTest {
    @AutoClose
    private CloseableDSLContext dslContext = DSL.using("jdbc:h2:mem:test", "sa", "sa");

    private DataEncryptionKeyAccessor dataEncryptionKeyAccessor = new TableStorageDataEncryptionKeyAccessor(dslContext);

    @BeforeEach
    void setup() {
        dslContext.createTable("_dek_storage")
                .column("content_id", SQLDataType.VARCHAR)
                .column("kek_label", SQLDataType.VARCHAR)
                .column("algorithm", SQLDataType.VARCHAR)
                .column("encrypted_dek", SQLDataType.BLOB)
                .column("iv", SQLDataType.BLOB)
                .execute();

        dataEncryptionKeyAccessor.addKeys(ContentReference.of("my-content"), Set.of(
                new StoredDataEncryptionKey(
                        DataEncryptionAlgorithm.of("xyz"),
                        WrappingKeyId.unwrapped(),
                        KeyBytes.copy(new byte[] { 0, 1, 2, 3}),
                        new byte[] {4, 5, 6, 7}
                ),
                new StoredDataEncryptionKey(
                        DataEncryptionAlgorithm.of("xyz"),
                        WrappingKeyId.of("my-kid"),
                        KeyBytes.copy(new byte[] { 10, 11, 12, 13}),
                        new byte[] {4, 5, 6, 7}
                )
        ));
    }

    @Test
    void retrieveKeys() {

        assertThat(dataEncryptionKeyAccessor.findAllKeys(ContentReference.of("my-content")))
                .allSatisfy(key -> {
                    assertThat(key.getDataEncryptionAlgorithm()).isEqualTo(DataEncryptionAlgorithm.of("xyz"));
                    assertThat(key.getInitializationVector()).isEqualTo(new byte[]{ 4, 5, 6, 7});
                })
                .satisfiesExactlyInAnyOrder(
                        key -> {
                            assertThat(key.getWrappingKeyId()).isEqualTo(WrappingKeyId.unwrapped());
                            assertThat(key.getEncryptedKeyData().getKeyBytes()).isEqualTo(new byte[] {0, 1, 2, 3});
                        },
                        key -> {
                            assertThat(key.getWrappingKeyId()).isEqualTo(WrappingKeyId.of("my-kid"));
                            assertThat(key.getEncryptedKeyData().getKeyBytes()).isEqualTo(new byte[] {10, 11, 12, 13});
                        }
                );
    }

    @Test
    void retrieveNonExistingKeys() {
        assertThat(dataEncryptionKeyAccessor.findAllKeys(ContentReference.of("other-reference"))).isEmpty();
    }

    @Test
    void addKey() {
        dataEncryptionKeyAccessor.addKey(ContentReference.of("my-content"), new StoredDataEncryptionKey(
                DataEncryptionAlgorithm.of("xyz"),
                WrappingKeyId.of("secondary-kid"),
                KeyBytes.copy(new byte[] { 90, 91, 92, 93}),
                new byte[] {4, 5, 6, 7}
        ));

        assertThat(dataEncryptionKeyAccessor.findAllKeys(ContentReference.of("my-content")))
                .allSatisfy(key -> {
                    assertThat(key.getDataEncryptionAlgorithm()).isEqualTo(DataEncryptionAlgorithm.of("xyz"));
                    assertThat(key.getInitializationVector()).isEqualTo(new byte[]{ 4, 5, 6, 7});
                })
                .satisfiesExactlyInAnyOrder(
                        key -> {
                            assertThat(key.getWrappingKeyId()).isEqualTo(WrappingKeyId.unwrapped());
                            assertThat(key.getEncryptedKeyData().getKeyBytes()).isEqualTo(new byte[] {0, 1, 2, 3});
                        },
                        key -> {
                            assertThat(key.getWrappingKeyId()).isEqualTo(WrappingKeyId.of("my-kid"));
                            assertThat(key.getEncryptedKeyData().getKeyBytes()).isEqualTo(new byte[] {10, 11, 12, 13});
                        },
                        key -> {
                            assertThat(key.getWrappingKeyId()).isEqualTo(WrappingKeyId.of("secondary-kid"));
                            assertThat(key.getEncryptedKeyData().getKeyBytes()).isEqualTo(new byte[] {90, 91, 92, 93});
                        }
                );
    }

    @Test
    void removeKey() {
        dataEncryptionKeyAccessor.removeKey(ContentReference.of("my-content"), WrappingKeyId.unwrapped());

        assertThat(dataEncryptionKeyAccessor.findAllKeys(ContentReference.of("my-content"))).singleElement()
                .satisfies(key -> {
                    assertThat(key.getWrappingKeyId()).isEqualTo(WrappingKeyId.of("my-kid"));
                    assertThat(key.getEncryptedKeyData().getKeyBytes()).isEqualTo(new byte[] {10, 11, 12, 13});
                });
    }

    @Test
    void clearKeys() {
        dataEncryptionKeyAccessor.clearKeys(ContentReference.of("my-content"));

        assertThat(dataEncryptionKeyAccessor.findAllKeys(ContentReference.of("my-content"))).isEmpty();
    }




}