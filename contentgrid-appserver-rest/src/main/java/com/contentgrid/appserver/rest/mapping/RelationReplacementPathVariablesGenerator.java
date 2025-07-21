package com.contentgrid.appserver.rest.mapping;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.Relation;
import java.util.Set;
import java.util.stream.Stream;
import lombok.NonNull;

class RelationReplacementPathVariablesGenerator implements ReplacementPathVariablesGenerator {

    private final Set<Class<? extends Relation>> supportedRelationClasses;

    @SafeVarargs
    public RelationReplacementPathVariablesGenerator(@NonNull Class<? extends Relation>... supportedRelationClasses) {
        this.supportedRelationClasses = Set.of(supportedRelationClasses);
    }

    @Override
    public Stream<ReplacementPathVariableValues> generateForApplication(@NonNull Application application) {
        return application.getRelations()
                .stream()
                .flatMap(relation -> Stream.of(relation, relation.inverse()))
                .filter(this::isSupported)
                .map(Relation::getSourceEndPoint)
                .filter(endpoint -> endpoint.getPathSegment() != null)
                .map(endpoint -> new ReplacementPathVariableValues(endpoint.getEntity().getPathSegment(),
                        endpoint.getPathSegment()));
    }

    private boolean isSupported(Relation relation) {
        return supportedRelationClasses.stream()
                .anyMatch(relationClass -> relationClass.isInstance(relation));
    }
}
