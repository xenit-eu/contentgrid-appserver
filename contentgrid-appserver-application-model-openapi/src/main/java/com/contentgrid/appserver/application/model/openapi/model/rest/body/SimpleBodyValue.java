package com.contentgrid.appserver.application.model.openapi.model.rest.body;

import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import java.util.List;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;

/**
 * A {@link BodyValue} representing a scalar field whose type is one of {@link SimpleAttribute.Type}.
 * <p>
 * Carries the attribute's constraints (e.g. {@link Constraint.AllowedValuesConstraint},
 * {@link Constraint.RegexPatternConstraint}) so that downstream generators (OpenAPI, HAL Forms)
 * can apply them without re-reading the application model.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@RequiredArgsConstructor
public class SimpleBodyValue extends BodyValue {

    @NonNull
    SimpleAttribute.Type type;

    @NonNull
    @Singular
    List<Constraint> constraints;

    public <T extends Constraint> Optional<T> getConstraint(Class<T> type) {
        return constraints.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }

    public SimpleBodyValue withConstraint(Constraint constraint) {
        return toBuilder().constraint(constraint).build();
    }

}
