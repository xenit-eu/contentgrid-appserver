package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

/**
 * Represents an attribute (field) of an entity.
 * 
 * An SimpleAttribute defines a property of an entity, including its name, database column,
 * data type, and constraints. Attributes map to columns in a database table.
 */
@Value
public class SimpleAttribute implements Attribute {

    /**
     * The name of the attribute.
     */
    @NonNull
    AttributeName name;

    String description;

    /**
     * The name of the database column this attribute maps to.
     */
    @NonNull
    ColumnName column;

    /**
     * The data type of this attribute.
     */
    @NonNull
    Type type;

    List<AttributeFlag> flags;

    /**
     * The list of constraints applied to this attribute.
     */
    List<Constraint> constraints;

    /**
     * Defines the data types supported for attributes.
     */
    public enum Type {
        LONG,
        DOUBLE,
        BOOLEAN,
        TEXT,
        DATETIME,
        UUID;

        public static final Set<Type> NATIVE_TYPES = Set.of(TEXT, UUID, LONG, DOUBLE, BOOLEAN, DATETIME);
    }

    @Builder
    SimpleAttribute(@NonNull AttributeName name, String description, ColumnName column,
            @NonNull Type type, @Singular List<AttributeFlag> flags, @Singular List<Constraint> constraints) {
        this.name = name;
        this.description = description;
        this.column = column == null ? name.toColumnName() : column;
        this.type = type;
        this.flags = flags;
        this.constraints = constraints;
        for (var flag : this.flags) {
            if (!flag.isSupported(this)) {
                throw new InvalidFlagException("Flag %s is not supported".formatted(flag.getClass().getSimpleName()));
            }
        }
    }


    @Override
    public List<ColumnName> getColumns() {
        return List.of(column);
    }

    /**
     * Finds a constraint of the specified type associated with this attribute.
     *
     * @param <C> the type of constraint to find
     * @param constraintClass the class object representing the constraint type
     * @return an Optional containing the constraint if found, or empty if not found
     */
    public <C extends Constraint> Optional<C> getConstraint(Class<C> constraintClass) {
        return getConstraints().stream()
                .filter(constraintClass::isInstance)
                .map(constraintClass::cast)
                .findAny();
    }

}
