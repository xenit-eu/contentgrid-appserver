package com.contentgrid.appserver.contentstore.impl.encryption;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.DataEncryptionAlgorithm;
import lombok.Getter;
import lombok.NonNull;

public class UnsupportedDecryptionAlgorithmException extends UndecryptableContentException{
    @Getter
    private final DataEncryptionAlgorithm algorithm;

    public UnsupportedDecryptionAlgorithmException(
            @NonNull ContentReference reference,
            @NonNull DataEncryptionAlgorithm algorithm
    ) {
        super(reference, "No configured engine can decrypt algorithm '%s'".formatted(algorithm.getValue()));
        this.algorithm = algorithm;
    }
}
