package com.contentgrid.appserver.application.model.links;

import com.contentgrid.appserver.application.model.exceptions.InvalidEntityLinkException;
import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import java.net.URI;
import java.util.Optional;
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

    public Optional<URI> getProfile() {
        return Optional.ofNullable(profile);
    }

    public Optional<PropertyPath> getOwner() {
        return Optional.ofNullable(owner);
    }

    public Optional<AttributePath> getStorage() {
        return Optional.ofNullable(storage);
    }

    public Optional<UriTemplateDefinition> getFallbackTemplate() {
        return Optional.ofNullable(fallbackTemplate);
    }
}
