package com.contentgrid.appserver.application.model.propertypath;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.HasAttributes;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.exceptions.AttributeNotFoundException;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.EntityName;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Resolves a {@link PropertyPath} on an entity against the application.
 */
@RequiredArgsConstructor
public class PropertyPathResolver {
    private final Application application;

    /**
     * The result of resolving a property path against a specific entity
     */
    public sealed interface ResolutionResult {

    }

    /**
     * The property path resolves to a relation
     */
    @Value
    public static class RelationResolutionResult implements ResolutionResult {

        /**
         * The resolved relation
         */
        Relation relation;
    }

    /**
     * The property path resolves to a specific attribute on an entity
     */
    @Value
    public static class AttributeResolutionResult implements ResolutionResult {

        /**
         * The entity that the resolved attribute is located on
         */
        EntityName entityName;

        /**
         * The path inside the entity that the resolved attribute is located on
         */
        AttributePath path;

        /**
         * The resolved attribute itself
         */
        Attribute attribute;
    }

    public ResolutionResult resolve(@NonNull EntityName entityName, @NonNull PropertyPath propertyPath) {
        var currentEntity = application.getRequiredEntityByName(entityName);
        var currentPath = propertyPath;

        while (currentPath != null) {
            switch (currentPath) {
                case AttributePath attributePath -> {
                    return new AttributeResolutionResult(
                            currentEntity.getName(),
                            attributePath,
                            resolveAttributePath(currentEntity, attributePath)
                    );
                }
                case SimpleRelationPath simpleRelationPath -> {
                    return new RelationResolutionResult(
                            application.getRequiredRelationForEntity(currentEntity, simpleRelationPath.getFirst()));
                }
                case PropertyPath.CrossesRelation relationPath -> {
                    var relation = application.getRequiredRelationForEntity(currentEntity, relationPath.getFirst());
                    // Move to the target entity
                    currentEntity = application.getRelationTargetEntity(relation);
                    currentPath = currentPath.getRest();
                }
            }
        }
        throw new IllegalStateException("Resolving property path '%s' on entity '%s' did not terminate at the end of the path".formatted(propertyPath, entityName));
    }

    public Relation resolveRelation(@NonNull EntityName entityName, @NonNull PropertyPath.ResolvesToRelation propertyPath) {
        if(resolve(entityName, propertyPath) instanceof RelationResolutionResult relationResolutionResult) {
            return relationResolutionResult.getRelation();
        }
        throw new IllegalStateException("Resolving property path '%s' on entity '%s' did not result in a relation".formatted(propertyPath, entityName));
    }

    public AttributeResolutionResult resolveAttribute(@NonNull EntityName entityName, @NonNull PropertyPath.ResolvesToAttribute propertyPath) {
        if(resolve(entityName, propertyPath) instanceof AttributeResolutionResult attributeResolutionResult) {
            return attributeResolutionResult;
        }
        throw new IllegalStateException("Resolving property path '%s' on entity '%s' did not result in an  attribute".formatted(propertyPath, entityName));
    }

    public static Attribute resolveAttributePath(@NonNull HasAttributes container, @NonNull AttributePath attributePath) {
        return switch (attributePath) {
            case SimpleAttributePath simpleAttributePath -> container.getAttributeByName(simpleAttributePath.getFirst())
                    .orElseThrow(() -> new AttributeNotFoundException("Attribute not found: " + simpleAttributePath.getFirst()));
            case CompositeAttributePath compositeAttributePath -> {
                var attr = container.getAttributeByName(compositeAttributePath.getFirst())
                        .orElseThrow(() -> new AttributeNotFoundException("Attribute not found: " + compositeAttributePath.getFirst()));
                if (attr instanceof CompositeAttribute compAttribute) {
                    yield resolveAttributePath(compAttribute, compositeAttributePath.getRest());
                }
                throw new AttributeNotFoundException("CompositeAttributePath goes over SimpleAttribute: " + attributePath);
            }
        };
    }

}
