package com.contentgrid.appserver.query.engine.jooq.thunk;

import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.MultivalueAttribute;
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
import lombok.NonNull;
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
    @EqualsAndHashCode(callSuper = false)
    static class SimpleAttributeNode extends CachedNode {
        @NonNull SimpleAttribute attribute;
        @NonNull Field<?> field;

        @Override
        public void store(PathElement pathElement, CachedNode node) {
            throw new InvalidThunkExpressionException("Path goes over non-existing attribute");
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    static class MultivalueAttributeNode extends CachedNode {
        @NonNull MultivalueAttribute attribute;
        @NonNull Field<?> field;

        @Override
        public void store(PathElement pathElement, CachedNode node) {
            throw new InvalidThunkExpressionException("Path goes over non-existing attribute");
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    static class CompositeAttributeNode extends CachedNode {
        @NonNull CompositeAttribute attribute;

        @Override
        public void store(PathElement pathElement, CachedNode node) {
            if (node instanceof SimpleAttributeNode || node instanceof MultivalueAttributeNode
                    || node instanceof CompositeAttributeNode) {
                super.store(pathElement, node);
            } else {
                throw new InvalidThunkExpressionException("Path goes over non-existing attribute");
            }
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    static class RelationNode extends CachedNode {
        @NonNull Relation relation;
        @NonNull TableName alias;

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
    @EqualsAndHashCode(callSuper = false)
    static class VariableNode extends CachedNode {
        @NonNull String name;
        @NonNull TableName alias;

        @Override
        public void store(PathElement pathElement, CachedNode node) {
            if (node instanceof VariableNode) {
                throw new InvalidThunkExpressionException(
                        JOOQSymbolicReferenceResolver.UNSUPPORTED_VARIABLE_EXCEPTION_MESSAGE);
            }
            super.store(pathElement, node);
        }
    }
}
