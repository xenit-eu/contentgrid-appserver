package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.links.LinkIdentity;
import com.contentgrid.appserver.application.model.links.LinkIdentity.NamedLink;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.version.Version;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;

public final class TextLinkVersion extends AbstractHashbasedVersion {

    private TextLinkVersion() {
    }

    public static Version calculate(
            @NonNull EntityIdentity entityIdentity,
            @NonNull LinkIdentity linkIdentity,
            @NonNull String value
    ) {
        List<String> hashArgs = Stream.of(
                entityIdentity.getEntityName().getValue(),
                entityIdentity.getEntityId().getValue().toString(),
                linkIdentity.rel().toString()).collect(Collectors.toList());
        if (linkIdentity instanceof NamedLink namedLink) {
            hashArgs.add(namedLink.name());
        }
        hashArgs.add(hash(value));

        return Version.exactly(hash(hashArgs.toArray(new String[0])));
    }
}