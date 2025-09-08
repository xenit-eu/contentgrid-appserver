package com.contentgrid.appserver.content.impl.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.content.api.ContentAccessor;
import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.UnwritableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.impl.encryption.engine.AesCtrEncryptionEngine;
import com.contentgrid.appserver.content.impl.encryption.engine.ContentEncryptionEngine.EncryptionParameters;
import com.contentgrid.appserver.content.impl.encryption.engine.DataEncryptionAlgorithm;
import com.contentgrid.appserver.content.impl.encryption.keys.DataEncryptionKeyAccessor;
import com.contentgrid.appserver.content.impl.encryption.keys.DataEncryptionKeyWrapper;
import com.contentgrid.appserver.content.impl.encryption.keys.KeyBytes;
import com.contentgrid.appserver.content.impl.encryption.keys.StoredDataEncryptionKey;
import com.contentgrid.appserver.content.impl.encryption.keys.UnencryptedSymmetricDataEncryptionKeyWrapper;
import com.contentgrid.appserver.content.impl.encryption.keys.WrappingKeyId;
import com.contentgrid.appserver.content.impl.encryption.testing.InMemoryDataEncryptionKeyAccessor;
import com.contentgrid.appserver.content.impl.encryption.testing.XorTestEncryptionEngine;
import com.contentgrid.appserver.content.impl.utils.testing.AbstractContentStoreBehaviorTest;
import com.contentgrid.appserver.content.impl.utils.testing.MockContentStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

class EncryptedContentStoreTest extends AbstractContentStoreBehaviorTest {
    @AutoClose
    private final ContentStore backingStorage = new MockContentStore();
    private final DataEncryptionKeyAccessor keyAccessor = new InMemoryDataEncryptionKeyAccessor();
    private final EncryptedContentStore xorContentStore = new EncryptedContentStore(
            backingStorage,
            keyAccessor,
            List.of(
                    new UnencryptedSymmetricDataEncryptionKeyWrapper(true)
            ),
            List.of(new XorTestEncryptionEngine())
    );

    @Getter
    private final ContentStore contentStore = new EncryptedContentStore(
            backingStorage,
            keyAccessor,
            List.of(
                    new EncryptOnlyDataEncryptionKeyWrapper(),
                    new FailingDataEncryptionKeyWrapper(),
                    new UnencryptedSymmetricDataEncryptionKeyWrapper(true)
            ),
            List.of(new AesCtrEncryptionEngine(128))
    );

    @Test
    void backingStoreIsEncrypted() throws UnwritableContentException, IOException, UnreadableContentException {
        var writer = write(contentStore, TEST_BYTES);

        var backingReader = readerFor(backingStorage, writer);

        try(var inputStream = backingReader.getContentInputStream()) {
            assertThat(inputStream.readAllBytes())
                    .asString()
                    .doesNotContain(new String(TEST_BYTES));
        }
    }

    @Test
    void passthroughUnencryptedReadToBackingStore()
            throws UnwritableContentException, IOException, UnreadableContentException {
        var writer = write(backingStorage, TEST_BYTES);

        var reader = readerFor(backingStorage, writer);
        try(var inputStream = reader.getContentInputStream()) {
            assertThat(inputStream).hasBinaryContent(TEST_BYTES);
        }
    }

    @Test
    void refusesStoreWithoutWrappers() {
        var store = new EncryptedContentStore(backingStorage, keyAccessor, List.of(), List.of(new AesCtrEncryptionEngine(128)));

        assertThatThrownBy(() -> store.writeContent(InputStream.nullInputStream()))
                .isInstanceOf(NoEncryptableDataEncryptionKeysException.class)
                .hasMessageContaining("No wrappers can encrypt data encryption keys");
    }

    @Test
    void refusesStoreWithoutEngine() {
        var store = new EncryptedContentStore(backingStorage, keyAccessor, List.of(new EncryptOnlyDataEncryptionKeyWrapper()), List.of());

        assertThatThrownBy(() -> store.writeContent(InputStream.nullInputStream()))
                .isInstanceOf(UnencryptableContentException.class)
                .hasMessageContaining("No encryption engine available");
    }

    @Test
    void decryptUsingMultipleEngines() throws UnwritableContentException, UnreadableContentException {
        var xorEncrypted = write(xorContentStore, TEST_BYTES);
        var aesEncrypted = write(contentStore, TEST_BYTES);

        var dualEngineStore = new EncryptedContentStore(
                backingStorage,
                keyAccessor,
                List.of(new UnencryptedSymmetricDataEncryptionKeyWrapper(true)),
                List.of(
                        new XorTestEncryptionEngine(),
                        new AesCtrEncryptionEngine(128)
                )
        );

        var xorReader = readerFor(dualEngineStore, xorEncrypted);
        var aesReader = readerFor(dualEngineStore, aesEncrypted);

        assertThat(xorReader.getContentInputStream()).hasBinaryContent(TEST_BYTES);
        assertThat(aesReader.getContentInputStream()).hasBinaryContent(TEST_BYTES);
    }

    @Test
    void decryptUnsupportedEngine() throws UnwritableContentException {
        var xorEncrypted = write(xorContentStore, TEST_BYTES);

        assertThatThrownBy(() -> readerFor(contentStore, xorEncrypted))
                .isInstanceOfSatisfying(UnsupportedDecryptionAlgorithmException.class, ex  -> {
                    assertThat(ex.getAlgorithm()).isEqualTo(DataEncryptionAlgorithm.of("XOR-TEST"));
                });
    }

    @Test
    void decryptKeyNoDecryptWrapper() throws UnwritableContentException {
        var encrypted = write(contentStore, TEST_BYTES);

        var otherStore = new EncryptedContentStore(
                backingStorage,
                keyAccessor,
                List.of(new EncryptOnlyDataEncryptionKeyWrapper()),
                List.of(new AesCtrEncryptionEngine(128))
        );

        assertThatThrownBy(() -> readerFor(otherStore, encrypted))
                .isInstanceOfSatisfying(NoDecryptableDataEncryptionKeysException.class, ex -> {
                    assertThat(ex.getStoredKeys()).containsExactlyInAnyOrder(
                            WrappingKeyId.unwrapped(),
                            EncryptOnlyDataEncryptionKeyWrapper.WRAPPING_KEY_ID,
                            FailingDataEncryptionKeyWrapper.WRAPPING_KEY_ID
                    );
                    assertThat(ex.getDecryptableKeys()).isEmpty();
                });
    }

    @Test
    void decryptKeyFailingWrapper() throws UnwritableContentException {
        var store = new EncryptedContentStore(
                backingStorage,
                keyAccessor,
                List.of(new FailingDataEncryptionKeyWrapper()),
                List.of(new AesCtrEncryptionEngine(128))
        );

        var encrypted = write(store, TEST_BYTES);

        assertThatThrownBy(() -> readerFor(contentStore, encrypted))
                .isInstanceOfSatisfying(NoDecryptableDataEncryptionKeysException.class, ex -> {
                    assertThat(ex.getStoredKeys()).containsExactly(
                            FailingDataEncryptionKeyWrapper.WRAPPING_KEY_ID
                    );
                    // Note: failing key wrapper has been removed from this list, even though it declares ability to decrypt
                    assertThat(ex.getDecryptableKeys()).containsExactly(
                            WrappingKeyId.unwrapped()
                    );
                    assertThat(ex.getCause()).isInstanceOfSatisfying(KeyUnwrappingFailedException.class, keyUnwrappingFailedException -> {
                        assertThat(keyUnwrappingFailedException.getWrappingKeyId()).isEqualTo(FailingDataEncryptionKeyWrapper.WRAPPING_KEY_ID);
                    });
                });
    }

    @Test
    void decryptKeyMultipleFailingWrappers() throws UnwritableContentException {
        var secondFailingWrapperKid = WrappingKeyId.of("second-failing-wrapper");
        var store = new EncryptedContentStore(
                backingStorage,
                keyAccessor,
                List.of(
                        new FailingDataEncryptionKeyWrapper(),
                        new FailingDataEncryptionKeyWrapper(secondFailingWrapperKid)
                ),
                List.of(new AesCtrEncryptionEngine(128))
        );

        var encrypted = write(store, TEST_BYTES);
        assertThatThrownBy(() -> readerFor(store, encrypted))
                .isInstanceOfSatisfying(NoDecryptableDataEncryptionKeysException.class, ex -> {
                    assertThat(ex.getStoredKeys()).containsExactlyInAnyOrder(
                            FailingDataEncryptionKeyWrapper.WRAPPING_KEY_ID,
                            secondFailingWrapperKid
                    );
                    assertThat(ex.getDecryptableKeys()).isEmpty();
                    assertThat(ex.getCause()).isInstanceOfSatisfying(KeyUnwrappingFailedException.class, keyUnwrappingFailedException -> {
                        // We can't really know the ordering here for sure, so either of those is fine
                        assertThat(keyUnwrappingFailedException.getWrappingKeyId()).isIn(
                                FailingDataEncryptionKeyWrapper.WRAPPING_KEY_ID,
                                secondFailingWrapperKid
                        );
                        assertThat(keyUnwrappingFailedException.getAllFailedWrappingKeyIds()).containsExactlyInAnyOrder(
                                FailingDataEncryptionKeyWrapper.WRAPPING_KEY_ID,
                                secondFailingWrapperKid
                        );
                        assertThat(keyUnwrappingFailedException.getSuppressed()).satisfiesExactly(suppressed -> {
                            assertThat(suppressed).isInstanceOfSatisfying(KeyUnwrappingFailedException.class, suppressedKeyUnwrapException -> {
                                // This is the other one, but we also don't know for sure which one it would be
                                assertThat(suppressedKeyUnwrapException.getWrappingKeyId()).isIn(
                                        FailingDataEncryptionKeyWrapper.WRAPPING_KEY_ID,
                                        secondFailingWrapperKid
                                ).isNotEqualTo(keyUnwrappingFailedException.getWrappingKeyId());
                            });
                        });
                    });
                });
    }

    @Test
    void decryptKeyNoSupportedWrapper() throws UnwritableContentException {
        var store = new EncryptedContentStore(
                backingStorage,
                keyAccessor,
                List.of(new FailingDataEncryptionKeyWrapper()),
                List.of(new AesCtrEncryptionEngine(128))
        );
        var encrypted = write(store, TEST_BYTES);

        var otherStore = new EncryptedContentStore(
                backingStorage,
                keyAccessor,
                List.of(new UnencryptedSymmetricDataEncryptionKeyWrapper(true)),
                List.of(new AesCtrEncryptionEngine(128))
        );


        assertThatThrownBy(() -> readerFor(otherStore, encrypted))
                .isInstanceOfSatisfying(NoDecryptableDataEncryptionKeysException.class, ex -> {
                    assertThat(ex.getStoredKeys()).containsExactly(FailingDataEncryptionKeyWrapper.WRAPPING_KEY_ID);
                    assertThat(ex.getDecryptableKeys()).containsExactly(WrappingKeyId.unwrapped());
                });

    }

    private static ContentAccessor write(ContentStore contentStore, byte[] content)
            throws UnwritableContentException {
        return contentStore.writeContent(new ByteArrayInputStream(content));
    }

    private static ContentReader readerFor(ContentStore contentStore, ContentAccessor accessor)
            throws UnreadableContentException {
        return contentStore.getReader(accessor.getReference(), ResolvedContentRange.fullRange(accessor.getContentSize()));
    }

    private static class EncryptOnlyDataEncryptionKeyWrapper implements DataEncryptionKeyWrapper {

        public static final WrappingKeyId WRAPPING_KEY_ID = WrappingKeyId.of("encrypt-only");

        @Override
        public Set<WrappingKeyId> getSupportedKeyIds() {
            return Set.of(WRAPPING_KEY_ID);
        }

        @Override
        public boolean canDecrypt() {
            return false;
        }

        @Override
        public boolean canEncrypt() {
            return true;
        }

        @Override
        public StoredDataEncryptionKey wrapEncryptionKey(EncryptionParameters dataEncryptionParameters) {
            return new StoredDataEncryptionKey(
                    dataEncryptionParameters.getAlgorithm(),
                    WRAPPING_KEY_ID,
                    // We can never decrypt anyways, might as well not write the key at all
                    KeyBytes.adopt(new byte[0]),
                    dataEncryptionParameters.getInitializationVector()
            );
        }

        @Override
        public EncryptionParameters unwrapEncryptionKey(
                StoredDataEncryptionKey encryptedDataEncryptionKey) {
            throw new IllegalStateException("Can not decrypt keys");
        }
    }

    @RequiredArgsConstructor
    private static class FailingDataEncryptionKeyWrapper implements DataEncryptionKeyWrapper {
        public static final WrappingKeyId WRAPPING_KEY_ID = WrappingKeyId.of("failing-wrapper");

        private final WrappingKeyId wrappingKeyId;

        public FailingDataEncryptionKeyWrapper() {
            this(WRAPPING_KEY_ID);
        }

        @Override
        public Set<WrappingKeyId> getSupportedKeyIds() {
            return Set.of(wrappingKeyId);
        }

        @Override
        public boolean canDecrypt() {
            return true;
        }

        @Override
        public boolean canEncrypt() {
            return true;
        }

        @Override
        public StoredDataEncryptionKey wrapEncryptionKey(EncryptionParameters dataEncryptionParameters) {
            return new StoredDataEncryptionKey(
                    dataEncryptionParameters.getAlgorithm(),
                    wrappingKeyId,
                    dataEncryptionParameters.getSecretKey().clone(),
                    dataEncryptionParameters.getInitializationVector()
            );
        }

        @Override
        public EncryptionParameters unwrapEncryptionKey(StoredDataEncryptionKey encryptedDataEncryptionKey) {
            throw new UnsupportedOperationException("Unwrapping the encryption key failed");
        }
    }

}

