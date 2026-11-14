package com.contentgrid.appserver.application.model.links;

import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import java.net.URI;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
final public class StoredEntityLink extends EntityLink {

    @NonNull
    PathSegmentName pathSegment;

    @NonNull
    AttributePath storage;

    @Builder
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
