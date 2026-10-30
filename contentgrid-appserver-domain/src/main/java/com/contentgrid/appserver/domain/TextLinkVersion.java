package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.links.LinkIdentity;
import com.contentgrid.appserver.application.model.links.LinkIdentity.NamedLink;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.version.Version;
import lombok.NonNull;

public final class TextLinkVersion {

    private TextLinkVersion() {
    }

    public static Version calculate(
            @NonNull EntityIdentity entityIdentity,
            @NonNull LinkIdentity linkIdentity,
            @NonNull String value
    ) {
        // The version covers which link of which entity item this is, and the stored text itself.
        // An unnamed link has no name, which is represented as an empty string
        var linkName = linkIdentity instanceof NamedLink namedLink ? namedLink.name() : "";

        return Version.exactly(ContentVersion.hash(
                entityIdentity.getEntityName().getValue(),
                entityIdentity.getEntityId().getValue().toString(),
                linkIdentity.rel().toString(),
                linkName,
                ContentVersion.hash(value)
        ));
    }
}