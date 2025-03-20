package com.contentgrid.appserver.application.model;

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
 * An Attribute defines a property of an entity, including its name, database column,
 * data type, and constraints. Attributes map to columns in a database table.
 */
@Value
@Builder
public class Attribute {

    /**
     * The name of the attribute.
     */
    @NonNull
    String name;

    /**
     * The name of the database column this attribute maps to.
     */
    @NonNull
    String column;

    /**
     * The data type of this attribute.
     */
    @NonNull
    Type type;

    /**
     * The list of constraints applied to this attribute.
     */
    @Singular
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
        CONTENT,
        AUDIT_METADATA,
        UUID;

        public static final Set<Type> NATIVE_TYPES = Set.of(TEXT, UUID, LONG, DOUBLE, BOOLEAN, DATETIME);
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
