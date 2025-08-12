package com.contentgrid.appserver.application.model.relations.flags;

import static com.contentgrid.appserver.application.model.relations.flags.EndpointFlagUtils.otherEndpoint;

import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequiredEndpointFlag implements RelationEndpointFlag {

    public static final RequiredEndpointFlag INSTANCE = new RequiredEndpointFlag();

    @Override
    public void checkSupported(Relation relation, RelationEndPoint endPoint) {
        if (endPoint.getName() == null) {
            throw new InvalidFlagException("Unnamed relation endpoint can not be required");
        }
        var otherEndpoint = otherEndpoint(relation, endPoint);
        if (otherEndpoint.hasFlag(RequiredEndpointFlag.class)) {
            // Chicken and egg problem
            throw new InvalidFlagException("Source and target endpoints can not be both required");
        }

        if (Objects.equals(otherEndpoint.getEntity(), endPoint.getEntity())) {
            // Chicken and egg problem
            throw new InvalidFlagException("Source or target endpoint can not be required when on the same entity");
        }
    }

}
