package com.contentgrid.appserver.application.model.settings.encryption;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ContentEncryptionSettings {

    /**
     * Whether content of this application is encrypted.
     * <p>
     * The algorithms that are used are configured on the appserver itself, with the
     * {@code contentgrid.appserver.content.encryption.engine.algorithms} and
     * {@code contentgrid.appserver.content.encryption.wrapper.algorithms} properties.
     */
    boolean enabled;
}
