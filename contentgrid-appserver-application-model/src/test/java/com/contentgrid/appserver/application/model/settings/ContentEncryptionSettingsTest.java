package com.contentgrid.appserver.application.model.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.exceptions.InvalidSettingsException;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionEngineAlgorithm;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionKeyWrapperAlgorithm;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentEncryptionSettingsTest {

    @Test
    void defaults() {
        var settings = ContentEncryptionSettings.builder().build();

        assertEquals(List.of(ContentEncryptionEngineAlgorithm.AES128_CTR), settings.getEncryptionEngineAlgorithms());
        assertEquals(List.of(ContentEncryptionKeyWrapperAlgorithm.NONE), settings.getKeyWrapperAlgorithms());
    }

    @Test
    void multipleContentEncryptionEngineAlgorithms() {
        var settings = ContentEncryptionSettings.builder()
                .encryptionEngineAlgorithm(ContentEncryptionEngineAlgorithm.AES256_CTR)
                .encryptionEngineAlgorithm(ContentEncryptionEngineAlgorithm.AES128_CTR)
                .build();

        assertEquals(ContentEncryptionEngineAlgorithm.AES256_CTR,
                settings.getEncryptionEngineAlgorithms().getFirst());
        assertEquals(ContentEncryptionEngineAlgorithm.AES128_CTR,
                settings.getEncryptionEngineAlgorithms().getLast());
    }

    @Test
    void unsupportedEncryptionEngineAlgorithmForEncryptionFirst() {
        // Content encryption engine algorithms is a list of algorithms.
        // Only the first algorithm in the list is used for encryption,
        // while all algorithms in the list will be used for decryption.
        // ALFRESCO is the only algorithm that doesn't support encryption,
        // which means it can't be put first in the list.
        var builder = ContentEncryptionSettings.builder()
                .encryptionEngineAlgorithm(ContentEncryptionEngineAlgorithm.ALFRESCO)
                .encryptionEngineAlgorithm(ContentEncryptionEngineAlgorithm.AES128_CTR);

        assertThrows(InvalidSettingsException.class, builder::build);
    }

    @Test
    void unsupportedEncryptionEngineAlgorithmForEncryptionLast() {
        var settings = ContentEncryptionSettings.builder()
                .encryptionEngineAlgorithm(ContentEncryptionEngineAlgorithm.AES256_CTR)
                .encryptionEngineAlgorithm(ContentEncryptionEngineAlgorithm.ALFRESCO)
                .build();

        assertEquals(ContentEncryptionEngineAlgorithm.AES256_CTR,
                settings.getEncryptionEngineAlgorithms().getFirst());
        assertEquals(ContentEncryptionEngineAlgorithm.ALFRESCO,
                settings.getEncryptionEngineAlgorithms().getLast());
    }

}
