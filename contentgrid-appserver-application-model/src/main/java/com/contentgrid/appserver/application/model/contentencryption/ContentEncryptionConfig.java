package com.contentgrid.appserver.application.model.contentencryption;

import java.util.Set;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class ContentEncryptionConfig {

    boolean enabled;

    @Singular
    Set<ContentEncryptionEngineAlgorithm> encryptionEngineAlgorithms;

    @Singular
    Set<ContentEncryptionKeyWrapperAlgorithm> keyWrapperAlgorithms;

    public static ContentEncryptionConfig disabled() {
        return ContentEncryptionConfig.builder().build();
    }
}
