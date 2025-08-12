package com.contentgrid.appserver.application.model.relations.flags;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EndpointFlagUtils {

    static @NonNull Relation.RelationEndPoint otherEndpoint(Relation relation, RelationEndPoint endPoint) {
        return Stream.of(relation.getSourceEndPoint(), relation.getTargetEndPoint())
                .filter(Predicate.not(Predicate.isEqual(endPoint)))
                .findFirst()
                .orElseThrow();
    }
}
