package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.domain.values.version.Version;
import lombok.NonNull;

/**
 * The data that is stored for a {@link com.contentgrid.appserver.application.model.links.StoredEntityLink}.
 * <p>
 * Which variant applies is determined by the attribute the link stores its data in.
 */
public sealed interface StoredLinkValue {

    record TextValue(@NonNull StoredLinkValue.VersionedString versionedString) implements StoredLinkValue {}
    record ContentValue(@NonNull ContentApi.Content content) implements StoredLinkValue {}

    record VersionedString(@NonNull String stringValue, Version version) {}

}
