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
import com.contentgrid.appserver.content.impl.utils.testing.InMemoryMockContentStore;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.junit.jupiter.api.Test;

class EncryptedContentStoreTest extends AbstractContentStoreBehaviorTest {
    private final ContentStore backingStorage = new InMemoryMockContentStore();
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

        assertThatThrownBy(store::createNewWriter)
                .isInstanceOf(NoEncryptableDataEncryptionKeysException.class)
                .hasMessageContaining("No wrappers can encrypt data encryption keys");
    }

    @Test
    void refusesStoreWithoutEngine() {
        var store = new EncryptedContentStore(backingStorage, keyAccessor, List.of(new EncryptOnlyDataEncryptionKeyWrapper()), List.of());

        assertThatThrownBy(store::createNewWriter)
                .isInstanceOf(UnencryptableContentException.class)
                .hasMessageContaining("No encryption engine available");
    }

    @Test
    void decryptUsingMultipleEngines() throws UnwritableContentException, IOException, UnreadableContentException {
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
    void decryptUnsupportedEngine() throws UnwritableContentException, IOException {
        var xorEncrypted = write(xorContentStore, TEST_BYTES);

        assertThatThrownBy(() -> readerFor(contentStore, xorEncrypted))
                .isInstanceOfSatisfying(UnsupportedDecryptionAlgorithmException.class, ex  -> {
                    assertThat(ex.getAlgorithm()).isEqualTo(DataEncryptionAlgorithm.of("XOR-TEST"));
                });
    }

    @Test
    void decryptKeyUnsupportedWrapper() throws UnwritableContentException, IOException {
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
                            EncryptOnlyDataEncryptionKeyWrapper.WRAPPING_KEY_ID
                    );
                    assertThat(ex.getDecryptableKeys()).isEmpty();
                });
    }

    private static ContentAccessor write(ContentStore contentStore, byte[] content)
            throws UnwritableContentException, IOException {
        var writer = contentStore.createNewWriter();
        try(var outputStream = writer.getContentOutputStream()) {
            outputStream.write(content);
        }
        return writer;
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
}