package com.contentgrid.appserver.application.model.settings.encryption;

import com.contentgrid.appserver.application.model.exceptions.InvalidSettingsException;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
public class ContentEncryptionSettings {

    List<ContentEncryptionEngineAlgorithm> encryptionEngineAlgorithms;

    List<ContentEncryptionKeyWrapperAlgorithm> keyWrapperAlgorithms;

    @Builder
    private ContentEncryptionSettings(@Singular Set<ContentEncryptionEngineAlgorithm> encryptionEngineAlgorithms,
            @Singular Set<ContentEncryptionKeyWrapperAlgorithm> keyWrapperAlgorithms) {
        this.encryptionEngineAlgorithms = encryptionEngineAlgorithms.isEmpty() ?
                List.of(ContentEncryptionEngineAlgorithm.AES128_CTR) : List.copyOf(encryptionEngineAlgorithms);
        this.keyWrapperAlgorithms = keyWrapperAlgorithms.isEmpty() ?
                List.of(ContentEncryptionKeyWrapperAlgorithm.NONE) : List.copyOf(keyWrapperAlgorithms);
        if (this.encryptionEngineAlgorithms.getFirst() == ContentEncryptionEngineAlgorithm.ALFRESCO) {
            throw new InvalidSettingsException("Content encryption algorithm ALFRESCO can't be used for encryption, a different algorithm must be used as first element in the list.");
        }
    }
}
