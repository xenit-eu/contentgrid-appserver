package com.contentgrid.appserver.application.model.links;

import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import java.net.URI;
import lombok.Builder;
import lombok.NonNull;

final public class PlainEntityLink extends EntityLink {

    @Builder
    public PlainEntityLink(
            @NonNull LinkIdentity identity,
            URI profile,
            PropertyPath owner,
            @NonNull UriTemplateDefinition fallbackTemplate
    ) {
        super(identity, profile, owner, fallbackTemplate);
    }

    public UriTemplateDefinition getTemplate() {
        return getFallbackTemplate().orElseThrow();
    }
}
