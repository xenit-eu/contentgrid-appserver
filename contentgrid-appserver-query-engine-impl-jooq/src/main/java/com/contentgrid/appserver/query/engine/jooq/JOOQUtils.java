package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.util.UUID;
import lombok.experimental.UtilityClass;
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
        return DSL.table(tableName.getValue());
    }

    public static Table<?> resolveTable(Entity entity, TableName alias) {
        return resolveTable(entity.getTable(), alias);
    }

    public static Table<?> resolveTable(TableName tableName, TableName alias) {
        return DSL.table(tableName.getValue()).as(alias.getValue());
    }

    public static Field<?> resolveField(TableName alias, SimpleAttribute attribute) {
        return resolveField(alias, attribute.getColumn(), attribute.getType(), attribute.hasConstraint(RequiredConstraint.class));
    }

    public static Field<?> resolveField(SimpleAttribute attribute) {
        return resolveField(attribute.getColumn(), attribute.getType(), attribute.hasConstraint(RequiredConstraint.class));
    }

    public static Field<?> resolveField(ColumnName column, SimpleAttribute.Type type, boolean required) {
        return DSL.field(column.getValue(), resolveType(type, required));
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

    private static DataType<?> resolveType(SimpleAttribute.Type type, boolean required) {
        var dataType = switch (type) {
            case UUID -> SQLDataType.UUID;
            case TEXT -> SQLDataType.CLOB;
            case LONG -> SQLDataType.BIGINT;
            case DOUBLE -> SQLDataType.DECIMAL;
            case BOOLEAN -> SQLDataType.BOOLEAN;
            case DATETIME -> SQLDataType.INSTANT;
        };
        return dataType.nullable(!required);
    }
}
