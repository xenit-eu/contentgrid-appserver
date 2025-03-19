package com.contentgrid.appserver.application.model;

import com.contentgrid.appserver.application.model.constraints.Constraint;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class Attribute {

    @NonNull
    String name;

    @NonNull
    String column;

    @NonNull
    Type type;

    @Singular
    List<Constraint> constraints;

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

    public <C extends Constraint> Optional<C> getConstraint(Class<C> constraintClass) {
        return getConstraints().stream()
                .filter(constraintClass::isInstance)
                .map(constraintClass::cast)
                .findAny();
    }

}
