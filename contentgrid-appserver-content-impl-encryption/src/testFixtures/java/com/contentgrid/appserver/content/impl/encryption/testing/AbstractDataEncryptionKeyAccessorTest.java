package com.contentgrid.appserver.content.impl.encryption.testing;


import static org.assertj.core.api.Assertions.*;

import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.impl.encryption.engine.DataEncryptionAlgorithm;
import com.contentgrid.appserver.content.impl.encryption.keys.DataEncryptionKeyAccessor;
import com.contentgrid.appserver.content.impl.encryption.keys.KeyBytes;
import com.contentgrid.appserver.content.impl.encryption.keys.StoredDataEncryptionKey;
import com.contentgrid.appserver.content.impl.encryption.keys.WrappingKeyId;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractDataEncryptionKeyAccessorTest {

    public static final ContentReference CONTENT_REFERENCE = ContentReference.of("my-content");
    public static final DataEncryptionAlgorithm DATA_ENCRYPTION_ALGORITHM = DataEncryptionAlgorithm.of("xyz");
    public static final byte[] IV = {4, 5, 6, 7};
    public static final byte[] KEY_1 = {0, 1, 2, 3};
    public static final WrappingKeyId KEY_2_ID = WrappingKeyId.of("secondary-kid");
    public static final byte[] KEY_2 = {10, 11, 12, 13};
    public static final WrappingKeyId KEY_1_ID = WrappingKeyId.of("primary-kid");

    protected abstract DataEncryptionKeyAccessor getDataEncryptionKeyAccessor();

    @BeforeEach
    protected void setup() {
        getDataEncryptionKeyAccessor().addKeys(CONTENT_REFERENCE, Set.of(
                new StoredDataEncryptionKey(
                        DATA_ENCRYPTION_ALGORITHM,
                        KEY_1_ID,
                        KeyBytes.copy(KEY_1),
                        IV
                ),
                new StoredDataEncryptionKey(
                        DATA_ENCRYPTION_ALGORITHM,
                        KEY_2_ID,
                        KeyBytes.copy(KEY_2),
                        IV
                )
        ));
    }

    @Test
    void retrieveKeys() {

        assertThat(getDataEncryptionKeyAccessor().findAllKeys(CONTENT_REFERENCE))
                .allSatisfy(key -> {
                    assertThat(key.getDataEncryptionAlgorithm()).isEqualTo(DATA_ENCRYPTION_ALGORITHM);
                    assertThat(key.getInitializationVector()).isEqualTo(IV);
                })
                .satisfiesExactlyInAnyOrder(
                        key -> {
                            assertThat(key.getWrappingKeyId()).isEqualTo(KEY_1_ID);
                            assertThat(key.getEncryptedKeyData().getKeyBytes()).isEqualTo(KEY_1);
                        },
                        key -> {
                            assertThat(key.getWrappingKeyId()).isEqualTo(KEY_2_ID);
                            assertThat(key.getEncryptedKeyData().getKeyBytes()).isEqualTo(KEY_2);
                        }
                );
    }

    @Test
    void retrieveNonExistingKeys() {
        assertThat(getDataEncryptionKeyAccessor().findAllKeys(ContentReference.of("other-reference"))).isEmpty();
    }

    @Test
    void addKey() {
        getDataEncryptionKeyAccessor().addKey(CONTENT_REFERENCE, new StoredDataEncryptionKey(
                DATA_ENCRYPTION_ALGORITHM,
                WrappingKeyId.of("additional-kid"),
                KeyBytes.copy(new byte[] { 90, 91, 92, 93}),
                IV
        ));

        assertThat(getDataEncryptionKeyAccessor().findAllKeys(CONTENT_REFERENCE))
                .allSatisfy(key -> {
                    assertThat(key.getDataEncryptionAlgorithm()).isEqualTo(DATA_ENCRYPTION_ALGORITHM);
                    assertThat(key.getInitializationVector()).isEqualTo(IV);
                })
                .satisfiesExactlyInAnyOrder(
                        key -> {
                            assertThat(key.getWrappingKeyId()).isEqualTo(KEY_1_ID);
                            assertThat(key.getEncryptedKeyData().getKeyBytes()).isEqualTo(KEY_1);
                        },
                        key -> {
                            assertThat(key.getWrappingKeyId()).isEqualTo(KEY_2_ID);
                            assertThat(key.getEncryptedKeyData().getKeyBytes()).isEqualTo(KEY_2);
                        },
                        key -> {
                            assertThat(key.getWrappingKeyId()).isEqualTo(WrappingKeyId.of("additional-kid"));
                            assertThat(key.getEncryptedKeyData().getKeyBytes()).isEqualTo(new byte[] {90, 91, 92, 93});
                        }
                );
    }

    @Test
    void removeKey() {
        getDataEncryptionKeyAccessor().removeKey(CONTENT_REFERENCE, KEY_1_ID);

        assertThat(getDataEncryptionKeyAccessor().findAllKeys(CONTENT_REFERENCE)).singleElement()
                .satisfies(key -> {
                    assertThat(key.getWrappingKeyId()).isEqualTo(KEY_2_ID);
                    assertThat(key.getEncryptedKeyData().getKeyBytes()).isEqualTo(KEY_2);
                });
    }

    @Test
    void clearKeys() {
        getDataEncryptionKeyAccessor().clearKeys(CONTENT_REFERENCE);

        assertThat(getDataEncryptionKeyAccessor().findAllKeys(CONTENT_REFERENCE)).isEmpty();
    }

}
