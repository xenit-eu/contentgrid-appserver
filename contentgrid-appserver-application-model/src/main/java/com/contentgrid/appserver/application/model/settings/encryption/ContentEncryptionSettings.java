package com.contentgrid.appserver.application.model.settings.encryption;

import com.contentgrid.appserver.application.model.settings.ApplicationSettings;
import java.util.Set;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class ContentEncryptionSettings implements ApplicationSettings {

    @Singular
    Set<ContentEncryptionEngineAlgorithm> encryptionEngineAlgorithms;

    @Singular
    Set<ContentEncryptionKeyWrapperAlgorithm> keyWrapperAlgorithms;
}
