package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.mapper.AbstractDescendingAttributeMapper;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Validation mapper that applies constraint validation to attributes.
 * <p>
 * Simple attributes are validated directly, composite attributes are recursively unpacked into its components before they are validated in the same way.
 * 
 * @param <T> the constraint type this mapper validates
 */
@RequiredArgsConstructor
public class AttributeConstraintValidationDataMapper<T extends Constraint> extends AbstractDescendingAttributeMapper {

    @NonNull
    private final ConstraintValidator<T> validator;

    @Override
    protected Optional<DataEntry> mapSimpleAttribute(SimpleAttribute simpleAttribute, DataEntry inputData)
            throws InvalidDataException {
        if (simpleAttribute.hasConstraint(validator.getConstraintType())) {
            var constraint = simpleAttribute.getConstraint(validator.getConstraintType()).orElseThrow();
            validator.validate(constraint, inputData);
        }
        return Optional.of(inputData);
    }

    @Override
    protected Optional<DataEntry> mapCompositeAttributeUnsupportedDatatype(CompositeAttribute attribute,
            DataEntry inputData) {
        // Composite attributes don't have constraints, so just pass on the input data
        // Another mapper would be responsible for handing this unsupported type
        return Optional.of(inputData);
    }

    public interface ConstraintValidator<T extends Constraint> {

        Class<T> getConstraintType();

        void validate(T constraint, DataEntry dataEntry) throws InvalidDataException;
    }
}
