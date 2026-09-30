package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.domain.ContentApi.Content;
import com.contentgrid.appserver.domain.values.version.Version;
import lombok.NonNull;
import lombok.Value;

/**
 * The data that is stored for a {@link com.contentgrid.appserver.application.model.links.StoredEntityLink}.
 * <p>
 * Which variant applies is determined by the attribute the link stores its data in.
 */
public sealed interface StoredLinkValue {

    Version getVersion();

    @Value
    class TextValue implements StoredLinkValue {

        @NonNull
        String value;

        @NonNull
        Version version;
    }

    @Value
    class ContentValue implements StoredLinkValue {

        @NonNull
        Content content;

        @Override
        public Version getVersion() {
            return content.getVersion();
        }
    }

}
