package com.contentgrid.appserver.content.impl.encryption;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.api.ContentWriter;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.UnwritableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.impl.encryption.engine.ContentEncryptionEngine;
import com.contentgrid.appserver.content.impl.encryption.engine.ContentEncryptionEngine.EncryptionParameters;
import com.contentgrid.appserver.content.impl.encryption.engine.DataEncryptionAlgorithm;
import com.contentgrid.appserver.content.impl.encryption.keys.DataEncryptionKeyAccessor;
import com.contentgrid.appserver.content.impl.encryption.keys.DataEncryptionKeyWrapper;
import com.contentgrid.appserver.content.impl.encryption.keys.StoredDataEncryptionKey;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EncryptedContentStore implements ContentStore {
    private final ContentStore delegate;
    private final DataEncryptionKeyAccessor dataEncryptionKeyAccessor;
    private final List<DataEncryptionKeyWrapper> dataEncryptionKeyWrappers;
    private final List<ContentEncryptionEngine> encryptionEngines;

    private Stream<DataEncryptionKeyWrapper> wrappersFor(Predicate<DataEncryptionKeyWrapper> predicate) {
        return dataEncryptionKeyWrappers.stream()
                .filter(predicate);
    }

    private Optional<ContentEncryptionEngine> engineFor(DataEncryptionAlgorithm algorithm) {
        return encryptionEngines.stream()
                .filter(ee -> ee.supports(algorithm))
                .findFirst();
    }

    @Override
    public ContentReader getReader(ContentReference contentReference, ResolvedContentRange contentRange)
            throws UnreadableContentException {
        var encryptedDEKs = dataEncryptionKeyAccessor.findAllKeys(contentReference);
        if(encryptedDEKs.isEmpty()) {
            // If we don't have encryption keys at all, the content must not be encrypted at all.
            // Forward to delegate reader for plain-text access
            return delegate.getReader(contentReference, contentRange);
        }

        DecryptionConfig decryptionConfig;
        try {
            decryptionConfig = decryptEncryptionParameters(encryptedDEKs);
        } catch (Exception e) {
            var ex = createUndecryptableContentException(contentReference, encryptedDEKs);
            ex.initCause(e);
            throw ex;
        } finally {
            encryptedDEKs.forEach(StoredDataEncryptionKey::destroy);
        }

        if(decryptionConfig == null) {
            throw createUndecryptableContentException(contentReference, encryptedDEKs);
        }

        return decryptionConfig.encryptionEngine()
                .decrypt(
                        range -> delegate.getReader(contentReference, range),
                        decryptionConfig.encryptionParameters(),
                        contentRange
                );
    }

    private UndecryptableContentException createUndecryptableContentException(
            ContentReference contentReference,
            List<StoredDataEncryptionKey> encryptedDEKs
    ) {
        var availableKeyIds = encryptedDEKs.stream()
                .map(StoredDataEncryptionKey::getWrappingKeyId)
                .collect(Collectors.toSet());
        var supportedKeyIds = wrappersFor(DataEncryptionKeyWrapper::canDecrypt)
                .flatMap(wrapper -> wrapper.getSupportedKeyIds().stream())
                .collect(Collectors.toSet());

        if(Collections.disjoint(availableKeyIds, supportedKeyIds)) {
            return new NoDecryptableDataEncryptionKeysException(
                    contentReference,
                    availableKeyIds,
                    supportedKeyIds
            );
        } else {
            return new UnsupportedDecryptionAlgorithmException(
                    contentReference,
                    // All DEKs should have the same data encryption algorithm.
                    // Logically, the same stored content can only have been encrypted with one algorithm
                    encryptedDEKs.getFirst().getDataEncryptionAlgorithm()
            );
        }
    }

    @Override
    public ContentWriter createNewWriter() throws UnwritableContentException {
        ContentEncryptionEngine encryptionEngine;
        try {
            encryptionEngine = encryptionEngines.getFirst();
        } catch (NoSuchElementException ex) {
            var e = new UnencryptableContentException(ContentReference.of("<unknown>"), "No encryption engine available");
            e.initCause(ex);
            throw e;
        }
        var encryptionParameters = encryptionEngine.createNewParameters();

        // Encrypt the encryption parameters with all wrappers that are enabled for encryption
        var encryptedDeks = wrappersFor(DataEncryptionKeyWrapper::canEncrypt)
                .map(wrapper -> wrapper.wrapEncryptionKey(encryptionParameters))
                .collect(Collectors.toSet());

        if(encryptedDeks.isEmpty()) {
            // Do not allow writing encrypted content without being able to store our keys
            // The write would be successful, but we would not ever be able to decrypt the data again
            throw new NoEncryptableDataEncryptionKeysException(ContentReference.of("<unknown>"));
        }

        return new EncryptingContentWriter(
                delegate.createNewWriter(),
                outputStream -> encryptionEngine.encrypt(outputStream, encryptionParameters),
                contentReference -> {
                    try {
                        dataEncryptionKeyAccessor.addKeys(contentReference, encryptedDeks);
                    } finally {
                        encryptedDeks.forEach(StoredDataEncryptionKey::destroy);
                    }
                }
        );
    }

    @Override
    public void remove(ContentReference contentReference) throws UnwritableContentException {
        dataEncryptionKeyAccessor.clearKeys(contentReference);
        delegate.remove(contentReference);
    }

    record DecryptionConfig(
            EncryptionParameters encryptionParameters,
            ContentEncryptionEngine encryptionEngine
    ) {


    }

    private DecryptionConfig decryptEncryptionParameters(Collection<StoredDataEncryptionKey> encryptedDeks)
            throws Exception {
        Exception firstException = null;
        for (var wrapper : dataEncryptionKeyWrappers) {
            if (!wrapper.canDecrypt()) {
                continue;
            }
            var supportedKeyIds = wrapper.getSupportedKeyIds();
            for (var encryptedDek : encryptedDeks) {
                if(!supportedKeyIds.contains(encryptedDek.getWrappingKeyId())) {
                    continue;
                }
                var maybeEngine = engineFor(encryptedDek.getDataEncryptionAlgorithm());
                if(maybeEngine.isEmpty()) {
                    continue;
                }
                try {
                    var encryptionParameters = Objects.requireNonNull(wrapper.unwrapEncryptionKey(encryptedDek), "Wrapper %s unwrap of %s returned null".formatted(wrapper, encryptedDek.getWrappingKeyId()));
                    return new DecryptionConfig(
                            encryptionParameters,
                            maybeEngine.get()
                    );
                } catch(Exception ex) {
                    // Failure to unwrap: prepare exception that will be thrown when all decryptions fail
                    if(firstException == null) {
                        firstException = ex;
                    } else {
                        firstException.addSuppressed(ex);
                    }
                }
            }
        }

        if(firstException != null) {
            throw firstException;
        }

        return null;
    }
}
