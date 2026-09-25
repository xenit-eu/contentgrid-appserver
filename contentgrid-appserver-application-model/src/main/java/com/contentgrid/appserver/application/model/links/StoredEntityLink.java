package com.contentgrid.appserver.application.model.links;

import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import java.net.URI;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@SuperBuilder
final public class StoredEntityLink extends EntityLink {

    @NonNull
    List<PathSegmentName> pathSegments;

    @NonNull
    AttributePath storage;

    public StoredEntityLink(
            @NonNull LinkIdentity identity,
            URI profile,
            PropertyPath owner,
            @NonNull AttributePath storage,
            @NonNull List<PathSegmentName> pathSegments,
            UriTemplateDefinition fallbackTemplate
    ) {
        super(identity, profile, owner, fallbackTemplate);
        this.pathSegments = pathSegments;
        this.storage = storage;
    }
}
