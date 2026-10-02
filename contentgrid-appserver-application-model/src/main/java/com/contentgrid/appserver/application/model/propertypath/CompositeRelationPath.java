package com.contentgrid.appserver.application.model.propertypath;

import com.contentgrid.appserver.application.model.propertypath.PropertyPath.CrossesRelation;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.ResolvesToRelation;
import com.contentgrid.appserver.application.model.values.RelationName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Property path that crosses a relation
 * @param <R> The type that the property path resolves to. ({@link ResolvesToAttribute} or {@link ResolvesToRelation})
 */
@RequiredArgsConstructor
@EqualsAndHashCode
public abstract sealed class CompositeRelationPath<R extends PropertyPath> implements CrossesRelation {
    @NonNull
    private final RelationName relation;

    @Getter
    private final @NonNull R rest;

    @Override
    public @NonNull RelationName getFirst() {
        return relation;
    }

    @Override
    public String toString() {
        return "%s.%s".formatted(relation, rest);
    }

    public static <R extends PropertyPath> CompositeRelationPath<R> of(RelationName relation, R rest) {
        return (CompositeRelationPath<R>) switch (rest) {
            case ResolvesToAttribute resolvesToAttribute -> new CompositeRelationPathToAttribute(relation, resolvesToAttribute);
            case ResolvesToRelation resolvesToRelation -> new CompositeRelationPathToRelation(relation, resolvesToRelation);
        };
    }

    public static CompositeRelationPathToAttribute of(RelationName relationName, ResolvesToAttribute resolvesToAttribute) {
        return new CompositeRelationPathToAttribute(relationName, resolvesToAttribute);
    }

    /**
     * A property path that crosses a relation and resolves to an attribute.
     * <p>
     * This class should usually not be referenced directly; use {@link CompositeRelationPath}, {@link CrossesRelation} or {@link ResolvesToAttribute} as appropriate
     */
    public static final class CompositeRelationPathToAttribute extends CompositeRelationPath<ResolvesToAttribute> implements ResolvesToAttribute {

        private CompositeRelationPathToAttribute(@NonNull RelationName relation, @NonNull ResolvesToAttribute rest) {
            super(relation, rest);
        }
    }

    /**
     * A property path that crosses a relation and resolves to a relation.
     * <p>
     * This class should usually not be referenced directly; use {@link CompositeRelationPath}, {@link CrossesRelation} or {@link ResolvesToRelation} as appropriate
     */
    public static final class CompositeRelationPathToRelation extends CompositeRelationPath<ResolvesToRelation> implements ResolvesToRelation {
        private CompositeRelationPathToRelation(@NonNull RelationName relation, @NonNull ResolvesToRelation rest) {
            super(relation, rest);
        }
    }
}
