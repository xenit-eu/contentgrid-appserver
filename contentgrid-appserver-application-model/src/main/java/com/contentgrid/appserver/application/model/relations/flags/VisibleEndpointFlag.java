package com.contentgrid.appserver.application.model.relations.flags;

import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VisibleEndpointFlag implements RelationEndpointFlag {

    public static final VisibleEndpointFlag INSTANCE = new VisibleEndpointFlag();

    @Override
    public void checkSupported(Relation relation, RelationEndPoint endPoint) {
        if (endPoint.hasFlag(HiddenEndpointFlag.class)) {
            throw new InvalidFlagException("Visible and hidden endpoint flags are mutually exclusive");
        }

        if (endPoint.getName() == null) {
            throw new InvalidFlagException("Visible relation endpoint must have a name");
        }

        if (endPoint.getPathSegment() == null) {
            throw new InvalidFlagException("Visible relation endpoint must have a pathSegment");
        }

        if (endPoint.getLinkName() == null) {
            throw new InvalidFlagException("Visible relation endpoint must have a linkName");
        }
    }
}
