package com.contentgrid.appserver.domain.values;

import com.contentgrid.appserver.application.model.links.LinkIdentity;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.With;

/**
 * Unique identity of a specific link (optionally pinned to a specific version)
 */
@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class LinkRequest {
    @NonNull
    EntityName entityName;

    @NonNull
    EntityId entityId;

    @NonNull
    LinkIdentity linkIdentity;

    @NonNull
    @With
    VersionConstraint versionConstraint;

    public static LinkRequest forLink(EntityIdentity entityIdentity, LinkIdentity linkIdentity) {
        return forLink(entityIdentity.getEntityName(), entityIdentity.getEntityId(), linkIdentity);
    }
    public static LinkRequest forLink(EntityName entityName, EntityId entityId, LinkIdentity linkIdentity) {
        return new LinkRequest(entityName, entityId, linkIdentity, VersionConstraint.ANY);
    }
    public static LinkRequest forLink(EntityName entityName, EntityId entityId, LinkIdentity linkIdentity, VersionConstraint versionConstraint) {
        return new LinkRequest(entityName, entityId, linkIdentity, versionConstraint);
    }

    public EntityRequest toEntityRequest() {
        return EntityRequest.forEntity(getEntityName(), getEntityId(), getVersionConstraint());
    }

    public String toString() {
        return "Link '%s' on '%s' %s (matching version %s)".formatted(linkIdentity, entityName, entityId,
                versionConstraint);
    }

}
