package com.contentgrid.appserver.application.model.links;

import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import java.net.URI;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@SuperBuilder
final public class StoredEntityLink extends EntityLink {

    @NonNull
    PathSegmentName pathSegment;

    @NonNull
    AttributePath storage;

    public StoredEntityLink(
            @NonNull LinkIdentity identity,
            URI profile,
            PropertyPath owner,
            @NonNull AttributePath storage,
            @NonNull PathSegmentName pathSegment,
            UriTemplateDefinition fallbackTemplate
    ) {
        super(identity, profile, owner, fallbackTemplate);
        this.pathSegment = pathSegment;
        this.storage = storage;
    }
}
