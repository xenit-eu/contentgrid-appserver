package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.application.model.links.LinkIdentity;
import com.contentgrid.appserver.domain.StoredLinkValue;
import java.net.URI;
import lombok.NonNull;
import lombok.Value;

/**
 * A resolved link of an entity instance.
 * <p>
 * Mirrors {@link com.contentgrid.appserver.application.model.links.EntityLink}: a
 * {@link com.contentgrid.appserver.application.model.links.StoredEntityLink} that has a value resolves to a
 * {@link StoredLinkData}; every other link (including a stored link without value, using its fallback template)
 * resolves to a {@link PlainLinkData}.
 */
public sealed interface EntityLinkData {

    LinkIdentity getIdentity();

    URI getProfile();

    String getHref();

    @Value
    class PlainLinkData implements EntityLinkData {
        @NonNull
        LinkIdentity identity;
        URI profile;
        @NonNull
        String href;
    }

    @Value
    class StoredLinkData implements EntityLinkData {
        @NonNull
        LinkIdentity identity;
        URI profile;
        @NonNull
        String href;
        @NonNull
        StoredLinkValue value;
    }

}
