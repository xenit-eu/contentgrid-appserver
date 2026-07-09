package com.contentgrid.appserver.application.model.propertypath;

import com.contentgrid.appserver.application.model.propertypath.PropertyPath.CrossesRelation;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.ResolvesToRelation;
import com.contentgrid.appserver.application.model.values.RelationName;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Property path that traverses (and terminates in) a single relation name
 */
@RequiredArgsConstructor
@EqualsAndHashCode
public final class SimpleRelationPath implements CrossesRelation, ResolvesToRelation {
    @NonNull
    RelationName relationName;

    @Override
    public @NonNull RelationName getFirst() {
        return relationName;
    }

    @Override
    public ResolvesToRelation getRest() {
        return null;
    }

    @Override
    public String toString() {
        return relationName.toString();
    }
}
