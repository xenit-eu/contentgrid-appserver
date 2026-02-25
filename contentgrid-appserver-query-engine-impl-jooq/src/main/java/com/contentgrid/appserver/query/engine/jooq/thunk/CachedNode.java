package com.contentgrid.appserver.query.engine.jooq.thunk;

import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.thunx.predicates.model.SymbolicReference.PathElement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jooq.Field;

abstract sealed class CachedNode {

    private final Map<PathElement, CachedNode> cache = new HashMap<>();

    public Optional<CachedNode> find(PathElement pathElement) {
        return Optional.ofNullable(cache.get(pathElement));
    }

    public void store(PathElement pathElement, CachedNode node) {
        cache.put(pathElement, node);
    }

    public void clear() {
        cache.clear();
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    static class SimpleAttributeNode extends CachedNode {
        SimpleAttribute attribute;
        Field<?> field;

        @Override
        public void store(PathElement pathElement, CachedNode node) {
            throw new InvalidThunkExpressionException("Path goes over non-existing attribute");
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    static class CompositeAttributeNode extends CachedNode {
        CompositeAttribute attribute;

        @Override
        public void store(PathElement pathElement, CachedNode node) {
            if (node instanceof SimpleAttributeNode || node instanceof CompositeAttributeNode) {
                super.store(pathElement, node);
            } else {
                throw new InvalidThunkExpressionException("Path goes over non-existing attribute");
            }
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    static class RelationNode extends CachedNode {
        Relation relation;
        TableName alias;

        private boolean isMany() {
            return relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation;
        }

        @Override
        public void store(PathElement pathElement, CachedNode node) {
            var isMany = isMany();
            if (!isMany && node instanceof VariableNode) {
                throw new InvalidThunkExpressionException(
                        JOOQSymbolicReferenceResolver.UNSUPPORTED_VARIABLE_EXCEPTION_MESSAGE);
            } else if (isMany && !(node instanceof VariableNode)) {
                throw new InvalidThunkExpressionException(
                        "VariablePathElement is required in traversing a *-to-many relation, got '%s' of type %s."
                                .formatted(pathElement, pathElement.getClass().getSimpleName()));
            }
            super.store(pathElement, node);
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    static class VariableNode extends CachedNode {
        String name;
        TableName alias;
    }
}
