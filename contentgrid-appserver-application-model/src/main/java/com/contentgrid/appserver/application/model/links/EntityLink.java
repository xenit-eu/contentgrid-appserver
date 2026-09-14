package com.contentgrid.appserver.application.model.links;

import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import java.net.URI;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@SuperBuilder
public abstract sealed class EntityLink permits PlainEntityLink, StoredEntityLink {

    @Getter
    @NonNull
    LinkIdentity identity;
    URI profile;
    PropertyPath owner;
    UriTemplateDefinition fallbackTemplate;

    public Optional<URI> getProfile() {
        return Optional.ofNullable(profile);
    }

    public Optional<PropertyPath> getOwner() {
        return Optional.ofNullable(owner);
    }

    public Optional<UriTemplateDefinition> getFallbackTemplate() {
        return Optional.ofNullable(fallbackTemplate);
    }

}
