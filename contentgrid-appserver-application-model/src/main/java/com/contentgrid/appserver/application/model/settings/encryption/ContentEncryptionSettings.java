package com.contentgrid.appserver.application.model.settings.encryption;

import com.contentgrid.appserver.application.model.settings.ApplicationSettings;
import java.util.Set;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
public class ContentEncryptionSettings implements ApplicationSettings {

    Set<ContentEncryptionEngineAlgorithm> encryptionEngineAlgorithms;

    Set<ContentEncryptionKeyWrapperAlgorithm> keyWrapperAlgorithms;

    @Builder
    private ContentEncryptionSettings(@Singular Set<ContentEncryptionEngineAlgorithm> encryptionEngineAlgorithms,
            @Singular Set<ContentEncryptionKeyWrapperAlgorithm> keyWrapperAlgorithms) {
        this.encryptionEngineAlgorithms = encryptionEngineAlgorithms.isEmpty() ?
                Set.of(ContentEncryptionEngineAlgorithm.AES128_CTR) : encryptionEngineAlgorithms;
        this.keyWrapperAlgorithms = keyWrapperAlgorithms.isEmpty() ?
                Set.of(ContentEncryptionKeyWrapperAlgorithm.NONE) : keyWrapperAlgorithms;
    }
}
