package com.contentgrid.appserver.query.engine;

import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.TableName;
import lombok.experimental.UtilityClass;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

@UtilityClass
public class JOOQUtils {

    public static Table<?> resolveTable(Entity entity, TableName alias) {
        return resolveTable(entity.getTable(), alias);
    }

    public static Table<?> resolveTable(Entity entity, int aliasCount) {
        return resolveTable(entity.getTable(), aliasCount);
    }

    public static Table<?> resolveTable(TableName tableName, TableName alias) {
        return DSL.table(tableName.getValue()).as(alias.getValue());
    }

    public static Table<?> resolveTable(TableName tableName, int aliasCount) {
        return resolveTable(tableName, alias(tableName, aliasCount));
    }

    public static Field<?> resolveField(TableName alias, SimpleAttribute attribute) {
        return resolveField(alias, attribute.getColumn(), attribute.getType(), attribute.getConstraint(RequiredConstraint.class).isPresent());
    }

    public static Field<?> resolveField(SimpleAttribute attribute) {
        return resolveField(attribute.getColumn(), attribute.getType(), attribute.getConstraint(RequiredConstraint.class).isPresent());
    }

    public static Field<?> resolveField(ColumnName column, SimpleAttribute.Type type, boolean required) {
        return DSL.field(column.getValue(), resolveType(type, required));
    }

    public static Field<?> resolveField(TableName alias, ColumnName column, SimpleAttribute.Type type, boolean required) {
        return DSL.field(DSL.name(alias.getValue(), column.getValue()), resolveType(type, required));
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

    public static TableName alias(Entity entity, int aliasCount) {
        return alias(entity.getTable(), aliasCount);
    }

    public static TableName alias(TableName tableName, int aliasCount) {
        return TableName.of(tableName.getValue().charAt(0) + String.valueOf(aliasCount));
    }
}
