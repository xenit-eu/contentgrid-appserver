package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.ETagFlag;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.jooq.strategy.HasSourceTableColumnRef;
import com.contentgrid.appserver.query.engine.jooq.strategy.JOOQRelationStrategyFactory;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.jooq.Allow;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

@UtilityClass
public class JOOQUtils {

    public static Table<?> resolveTable(Entity entity) {
        return resolveTable(entity.getTable());
    }

    public static Table<?> resolveTable(TableName tableName) {
        return DSL.table(DSL.name(tableName.getValue()));
    }

    public static Table<?> resolveTable(Entity entity, TableName alias) {
        return resolveTable(entity.getTable(), alias);
    }

    public static Table<?> resolveTable(TableName tableName, TableName alias) {
        return DSL.table(DSL.name(tableName.getValue())).as(alias.getValue());
    }

    public static Field<?> resolveField(TableName alias, SimpleAttribute attribute) {
        return resolveField(alias, attribute.getColumn(), attribute.getType(), attribute.hasConstraint(RequiredConstraint.class));
    }

    public static Field<?> resolveField(SimpleAttribute attribute) {
        return resolveField(attribute.getColumn(), attribute.getType(), attribute.hasConstraint(RequiredConstraint.class));
    }

    public static Field<?> resolveField(ColumnName column, SimpleAttribute.Type type, boolean required) {
        return DSL.field(DSL.name(column.getValue()), resolveType(type, required));
    }

    public static Field<?> resolveField(TableName alias, ColumnName column, SimpleAttribute.Type type, boolean required) {
        return DSL.field(DSL.name(alias.getValue(), column.getValue()), resolveType(type, required));
    }

    public static Field<UUID> resolvePrimaryKey(Entity entity) {
        return resolvePrimaryKey(entity.getPrimaryKey());
    }

    public static Field<UUID> resolvePrimaryKey(SimpleAttribute primaryKey) {
        return (Field<UUID>) resolveField(primaryKey.getColumn(), primaryKey.getType(), true);
    }

    public static Field<UUID> resolvePrimaryKey(TableName alias, Entity entity) {
        return resolvePrimaryKey(alias, entity.getPrimaryKey());
    }

    public static Field<UUID> resolvePrimaryKey(TableName alias, SimpleAttribute primaryKey) {
        return (Field<UUID>) resolveField(alias, primaryKey.getColumn(), primaryKey.getType(), true);
    }

    public static Optional<Field<Long>> resolveVersionField(Entity entity) {
        return entity.getAttributes()
                .stream()
                .filter(attr -> attr.hasFlag(ETagFlag.class))
                .findFirst()
                .map(SimpleAttribute.class::cast)
                .map(attr -> (Field<Long>) resolveField(entity.getTable(), attr));
    }

    public static Field<?>[] resolveAttributeFields(Entity entity) {
        return Stream.concat(
                Stream.of(resolvePrimaryKey(entity)),
                entity.nestedAttributes()
                        .flatMap(entry -> switch (entry.getAttribute()) {
                            case SimpleAttribute simpleAttribute -> Stream.of(resolveField(simpleAttribute));
                            case CompositeAttribute ignored -> Stream.of();
                        })
        ).toArray(Field[]::new);
    }

    private static DataType<?> resolveType(SimpleAttribute.Type type, boolean required) {
        var dataType = switch (type) {
            case UUID -> SQLDataType.UUID;
            case TEXT -> SQLDataType.CLOB;
            case LONG -> SQLDataType.BIGINT;
            case DOUBLE -> SQLDataType.DECIMAL;
            case BOOLEAN -> SQLDataType.BOOLEAN;
            case DATE -> SQLDataType.LOCALDATE;
            case DATETIME -> SQLDataType.INSTANT;
        };
        return dataType.nullable(!required);
    }

    @Allow.PlainSQL
    static Condition generateFTSCondition(@NonNull Field<?> left, @NonNull Field<?> right, @NonNull String language) {
        return DSL.condition("to_tsvector(?, ?) @@ websearch_to_tsquery(?)", DSL.inline(language), left, right);
    }

    @Allow.PlainSQL
    static Field<String> normalize(Field<?> field) {
        return DSL.field(DSL.sql("normalize(?, NFKC)", field), String.class);
    }

    @Allow.PlainSQL
    static Field<String> prefixSearchNormalize(Field<?> field) {
        return DSL.field(DSL.sql("extensions.contentgrid_prefix_search_normalize(?)", field), String.class);
    }

    public static Field<?>[] resolveRelationFields(@NonNull Application application, @NonNull Entity entity) {
        return application.getRelationsForSourceEntity(entity)
                .stream()
                .flatMap(relation -> {
                    if (JOOQRelationStrategyFactory.forRelation(relation) instanceof HasSourceTableColumnRef<Relation> hasSourceTableColumnRef) {
                        return Stream.of(hasSourceTableColumnRef.getSourceTableColumnRef(application, relation));
                    }
                    return Stream.empty();
                })
                .toArray(Field[]::new);
    }

    public static Field<?>[] resolveAttributeAndRelationFields(@NonNull Application application, @NonNull Entity entity) {
        return Stream.concat(
                Arrays.stream(resolveAttributeFields(entity)),
                Arrays.stream(resolveRelationFields(application, entity))
        ).toArray(Field[]::new);
    }
}
