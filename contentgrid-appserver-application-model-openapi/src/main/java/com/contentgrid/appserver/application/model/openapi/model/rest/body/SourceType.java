package com.contentgrid.appserver.application.model.openapi.model.rest.body;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import lombok.NonNull;
import lombok.Value;

/**
 * A reference to where a {@link BodyValue} was generated from
 * <p>
 * This can be used to pinpoint which entity, attribute, relation or search filter a certain {@link BodyValue} is derived from.
 * It can be used (preferably only in very limited cases) to special-case behavior for particular sources
 */
public sealed interface SourceType {

    @Value
    class EntitySourceType implements SourceType {
        @NonNull
        EntityName entityName;

        @Override
        public AttributeSourceType nested(@NonNull AttributeName attributeName) {
            return new AttributeSourceType(entityName, new SimpleAttributePath(attributeName));
        }
    }
    @Value
    class AttributeSourceType implements SourceType {
        @NonNull
        EntityName entityName;

        @NonNull
        AttributePath attributePath;

        public AttributeSourceType nested(@NonNull AttributeName attributeName) {
            return new AttributeSourceType(entityName, attributePath.withSuffix(attributeName));
        }
    }

    @Value
    class RelationSourceType implements SourceType {
        @NonNull
        Relation relation;

        @Override
        public AttributeSourceType nested(@NonNull AttributeName attributeName) {
            return new AttributeSourceType(relation.getTargetEndPoint().getEntity(), new SimpleAttributePath(attributeName));
        }
    }

    @Value
    class SearchFilterSourceType implements SourceType {
        @NonNull
        EntityName entityName;

        @NonNull
        FilterName searchFilter;

        @Override
        public AttributeSourceType nested(@NonNull AttributeName attributeName) {
            throw new IllegalStateException("Can not descend down an attribute in a search filter");
        }
    }

    AttributeSourceType nested(@NonNull AttributeName attributeName);

}
