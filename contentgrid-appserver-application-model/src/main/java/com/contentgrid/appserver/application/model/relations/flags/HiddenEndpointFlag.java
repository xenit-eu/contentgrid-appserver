package com.contentgrid.appserver.application.model.relations.flags;

import static com.contentgrid.appserver.application.model.relations.flags.EndpointFlagUtils.otherEndpoint;

import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HiddenEndpointFlag implements RelationEndpointFlag {

    public static final HiddenEndpointFlag INSTANCE = new HiddenEndpointFlag();

    @Override
    public void checkSupported(Relation relation, RelationEndPoint endPoint) {
        if (endPoint.getPathSegment() != null) {
            throw new InvalidFlagException("Hidden relation endpoint can not have a pathSegment");
        }

        if (endPoint.getLinkName() != null) {
            throw new InvalidFlagException("Hidden relation endpoint can not have a linkName");
        }

        if (endPoint.getDescription() != null) {
            throw new InvalidFlagException("Hidden relation endpoint can not have a description");
        }

        var otherEndpoint = otherEndpoint(relation, endPoint);
        if (otherEndpoint.hasFlag(HiddenEndpointFlag.class)) {
            throw new InvalidFlagException("At least one endpoint must not be hidden");
        }
    }
}
