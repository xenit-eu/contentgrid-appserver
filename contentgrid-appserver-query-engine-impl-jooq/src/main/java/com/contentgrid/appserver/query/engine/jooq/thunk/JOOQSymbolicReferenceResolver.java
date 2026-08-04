package com.contentgrid.appserver.query.engine.jooq.thunk;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.HasAttributes;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import com.contentgrid.appserver.query.engine.jooq.thunk.CachedNode.CompositeAttributeNode;
import com.contentgrid.appserver.query.engine.jooq.thunk.CachedNode.RelationNode;
import com.contentgrid.appserver.query.engine.jooq.thunk.CachedNode.SimpleAttributeNode;
import com.contentgrid.appserver.query.engine.jooq.thunk.CachedNode.VariableNode;
import com.contentgrid.appserver.query.engine.jooq.thunk.Join.SourceColumnJoin;
import com.contentgrid.appserver.query.engine.jooq.thunk.Join.TargetColumnJoin;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference.PathElement;
import com.contentgrid.thunx.predicates.model.SymbolicReference.StringPathElement;
import com.contentgrid.thunx.predicates.model.SymbolicReference.VariablePathElement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.Getter;
import lombok.NonNull;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

class JOOQSymbolicReferenceResolver {

    static final String UNSUPPORTED_VARIABLE_EXCEPTION_MESSAGE =
            "cannot traverse symbolic reference using path element type %s, expected a %s"
                    .formatted(VariablePathElement.class.getSimpleName(), StringPathElement.class.getSimpleName());

    @NonNull
    private final Application application;

    @Getter
    @NonNull
    private final Entity rootEntity;

    @Getter
    @NonNull
    private final TableName rootAlias;

    private int aliasCount = 0;

    private final List<Join> joins = new ArrayList<>();

    private final CachedNode cache;

    private final Set<String> usedVariables = new HashSet<>();

    public JOOQSymbolicReferenceResolver(
            Application application, EntityName entityName
    ) {
        this.application = application;
        this.rootEntity = application.getRequiredEntityByName(entityName);
        this.rootAlias = generateAlias(this.rootEntity);
        this.cache = new VariableNode("entity", this.rootAlias);
    }

    private TableName generateAlias(Entity entity) {
        return generateAlias(entity.getTable());
    }

    private TableName generateAlias(TableName table) {
        var currentAlias = TableName.of(table.getValue().charAt(0) + String.valueOf(aliasCount));
        this.aliasCount += 1;
        return currentAlias;
    }

    public Condition wrapJoins(Function<JOOQSymbolicReferenceResolver, Condition> conditionFunction) {
        var resolver = newResolver();
        var condition = conditionFunction.apply(resolver);
        this.merge(resolver);
        return resolver.collect(condition);
    }

    private void merge(JOOQSymbolicReferenceResolver resolver) {
        this.usedVariables.addAll(resolver.usedVariables);
        this.aliasCount = Math.max(this.aliasCount, resolver.aliasCount);
    }

    private JOOQSymbolicReferenceResolver newResolver() {
        var result = new JOOQSymbolicReferenceResolver(this.application, this.rootEntity.getName());
        result.merge(this);
        return result;
    }

    public Field<?> resolvePath(List<PathElement> path) {
        Entity currentEntity = rootEntity;
        HasAttributes currentContainer = rootEntity;
        TableName currentAlias = rootAlias;
        CachedNode currentNode = cache;

        for (var index = 0; index < path.size(); index += 1) {
            var elem = path.get(index);
            var maybeNextNode = currentNode.find(elem);
            CachedNode nextNode;
            if (maybeNextNode.isPresent()) {
                nextNode = maybeNextNode.get();
            } else {
                nextNode = nextNode(currentNode, currentEntity, currentContainer, currentAlias, elem);
                currentNode.store(elem, nextNode);
            }

            switch (nextNode) {
                case SimpleAttributeNode simpleAttributeNode -> {
                    if (index + 1 < path.size()) {
                        // Not yet at end of path, but no nested attributes possible on a SimpleAttribute
                        throw new InvalidThunkExpressionException("Path goes over non-existing attribute");
                    }
                    return simpleAttributeNode.getField();
                }
                case CompositeAttributeNode compositeAttributeNode -> {
                    currentContainer = compositeAttributeNode.getAttribute();
                }
                case RelationNode relationNode -> {
                    currentEntity = application.getRelationTargetEntity(relationNode.getRelation());
                    currentContainer = currentEntity;
                    currentAlias = relationNode.getAlias();
                }
                case VariableNode variableNode -> {
                    currentAlias = variableNode.getAlias();
                }
            }

            currentNode = nextNode; // Advance to nextNode
        }
        throw new InvalidThunkExpressionException("Path does not end in SimpleAttribute");
    }

    private CachedNode nextNode(@NonNull CachedNode currentNode, @NonNull Entity currentEntity,
            @NonNull HasAttributes hasAttributes, @NonNull TableName currentAlias, @NonNull PathElement pathElement)
            throws InvalidThunkExpressionException {
        if (pathElement instanceof VariablePathElement variablePathElement) {
            return processVariable(currentNode, currentAlias, variablePathElement);
        }
        var name = getPathElementName(pathElement);

        // Check if pathElement references attribute
        Optional<Attribute> maybeAttribute = hasAttributes.getAttributeByName(AttributeName.of(name));
        if (maybeAttribute.isPresent()) {
            var attribute = maybeAttribute.get();
            return switch (attribute) {
                case SimpleAttribute simpleAttribute -> {
                    var field = JOOQUtils.resolveField(currentAlias, simpleAttribute);
                    yield new SimpleAttributeNode(simpleAttribute, field);
                }
                case CompositeAttribute compositeAttribute -> new CompositeAttributeNode(compositeAttribute);
            };
        }

        // Check if pathElement references relation
        Optional<Relation> maybeRelation = application.getRelationForEntity(currentEntity, RelationName.of(name));
        if (maybeRelation.isPresent()) {
            var relation = maybeRelation.get();
            return processRelation(relation, currentEntity, currentAlias);
        }

        // pathElement seems to reference a non-existing attribute/relation on the entity
        throw new InvalidThunkExpressionException(
                "Path element %s does not exist on entity %s".formatted(name, currentEntity));
    }

    private RelationNode processRelation(Relation relation, Entity currentEntity, TableName currentAlias) {
        var targetEntity = application.getRelationTargetEntity(relation);
        var targetTable = targetEntity.getTable();
        var nextAlias = switch (relation) {
            case SourceOneToOneRelation oneToOneRelation -> {
                var targetAlias = this.generateAlias(targetEntity.getTable());
                joins.add(new TargetColumnJoin(currentAlias, targetAlias, targetTable,
                        targetEntity.getPrimaryKey(),
                        oneToOneRelation.getTargetReference()));
                yield targetAlias;
            }
            case ManyToOneRelation manyToOneRelation -> {
                var targetAlias = this.generateAlias(targetEntity.getTable());
                joins.add(new TargetColumnJoin(currentAlias, targetAlias, targetTable,
                        targetEntity.getPrimaryKey(),
                        manyToOneRelation.getTargetReference()));
                yield targetAlias;
            }
            case TargetOneToOneRelation oneToOneRelation -> {
                var targetAlias = this.generateAlias(targetEntity.getTable());
                joins.add(new SourceColumnJoin(currentAlias, targetAlias, targetTable,
                        currentEntity.getPrimaryKey(),
                        oneToOneRelation.getSourceReference()));
                yield targetAlias;
            }
            // Skip joins for to-many relations, they will be done when processing the variable
            default -> currentAlias;
        };
        return new RelationNode(relation, nextAlias);
    }

    private VariableNode processVariable(CachedNode currentNode, TableName currentAlias, VariablePathElement variable) {
        var variableName = variable.getVariable().getName();

        if (currentNode instanceof RelationNode relationNode) {
            if (!usedVariables.add(variableName)) {
                throw new InvalidThunkExpressionException(
                        "Variable %s cannot be reused across different relation paths.".formatted(variableName));
            }

            var relation = relationNode.getRelation();
            var sourceEntity = application.getRelationSourceEntity(relation);
            var targetEntity = application.getRelationTargetEntity(relation);
            var targetTable = targetEntity.getTable();
            var nextAlias = switch (relation) {
                case OneToManyRelation oneToManyRelation -> {
                    var targetAlias = this.generateAlias(targetEntity.getTable());
                    joins.add(new SourceColumnJoin(currentAlias, targetAlias, targetTable,
                            sourceEntity.getPrimaryKey(),
                            oneToManyRelation.getSourceReference()));
                    yield targetAlias;
                }
                case ManyToManyRelation manyToManyRelation -> {
                    var joinTable = manyToManyRelation.getJoinTable();
                    var joinTableAlias = this.generateAlias(joinTable);
                    joins.add(new SourceColumnJoin(currentAlias, joinTableAlias, joinTable,
                            sourceEntity.getPrimaryKey(),
                            manyToManyRelation.getSourceReference()));
                    var targetAlias = this.generateAlias(targetEntity.getTable());
                    joins.add(new TargetColumnJoin(joinTableAlias, targetAlias, targetTable,
                            targetEntity.getPrimaryKey(),
                            manyToManyRelation.getTargetReference()));
                    yield targetAlias;
                }
                // previous path element was a one-to-one or many-to-one relation
                default -> throw new InvalidThunkExpressionException(UNSUPPORTED_VARIABLE_EXCEPTION_MESSAGE);
            };
            return new VariableNode(variableName, nextAlias);
        } else {
            // previous path element was not a relation
            throw new InvalidThunkExpressionException(UNSUPPORTED_VARIABLE_EXCEPTION_MESSAGE);
        }
    }

    private Condition collect(Condition condition) {
        SelectJoinStep<?> selectBuilder = null;
        Condition where = null;
        for (var join : joins) {
            if (selectBuilder == null) {
                selectBuilder = DSL.selectOne().from(JOOQUtils.resolveTable(application, join.getTargetTable(), join.getTargetAlias()));
                where = join.getCondition();
            } else {
                selectBuilder = selectBuilder.join(JOOQUtils.resolveTable(application, join.getTargetTable(), join.getTargetAlias()))
                        .on(join.getCondition());
            }
        }

        if (selectBuilder == null || where == null) {
            return condition;
        } else {
            return DSL.exists(selectBuilder.where(DSL.and(where, condition)));
        }
    }

    private static String getPathElementName(@NonNull PathElement elem) throws InvalidThunkExpressionException {
        if (elem instanceof StringPathElement string) {
            return ((Scalar<String>) string.getPath()).getValue();
        }
        throw new InvalidThunkExpressionException(UNSUPPORTED_VARIABLE_EXCEPTION_MESSAGE);
    }
}
