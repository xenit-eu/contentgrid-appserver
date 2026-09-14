package com.contentgrid.appserver.application.model.links;

import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import java.net.URI;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@SuperBuilder
final public class PlainEntityLink extends EntityLink {

    public PlainEntityLink(
            @NonNull LinkIdentity identity,
            URI profile,
            PropertyPath owner,
            @NonNull UriTemplateDefinition template
    ) {
        super(identity, profile, owner, template);
    }

    public UriTemplateDefinition getTemplate() {
        return getFallbackTemplate().orElseThrow();
    }
}
