package com.contentgrid.appserver.application.model.links;

import com.contentgrid.appserver.application.model.exceptions.InvalidEntityLinkException;
import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import java.net.URI;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
public class EntityLink {
    @NonNull
    LinkIdentity identity;

    URI profile;

    PropertyPath owner;

    AttributePath storage;

    UriTemplateDefinition fallbackTemplate;

    @Builder
    public EntityLink(
            @NonNull LinkIdentity identity,
            URI profile,
            PropertyPath owner,
            AttributePath storage,
            UriTemplateDefinition fallbackTemplate
    ) {
        this.identity = identity;
        this.profile = profile;
        this.owner = owner;
        this.storage = storage;
        this.fallbackTemplate = fallbackTemplate;

        if(this.storage == null && this.fallbackTemplate == null) {
            throw new InvalidEntityLinkException("Link %s must have either storage or a fallback template".formatted(identity));
        }

    }
}
