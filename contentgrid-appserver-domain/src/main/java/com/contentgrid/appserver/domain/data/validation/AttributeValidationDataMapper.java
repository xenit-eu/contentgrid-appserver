package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.mapper.AbstractDescendingAttributeMapper;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Validation mapper that applies validation to attributes.
 * <p>
 * Simple attributes are validated directly, composite attributes are recursively unpacked into its components before they are validated in the same way.
 * 
 */
@RequiredArgsConstructor
public class AttributeValidationDataMapper extends AbstractDescendingAttributeMapper {

    @NonNull
    private final List<Validator> validators;

    public AttributeValidationDataMapper(Validator validator) {
        this(List.of(validator));
    }

    public <T extends Constraint> AttributeValidationDataMapper(ConstraintValidator<T> constraintValidator) {
        this(List.of(new ConstraintValidatorAdapter<>(constraintValidator)));
    }

    @Override
    protected Optional<DataEntry> mapSimpleAttribute(AttributePath path, SimpleAttribute simpleAttribute, DataEntry inputData)
            throws InvalidDataException {
        var collector = new ValidationExceptionCollector<>(InvalidDataException.class);
        for (var validator : validators) {
            collector.use(() -> validator.validate(path, simpleAttribute, inputData));
        }
        collector.rethrow();
        return Optional.of(inputData);
    }

    @Override
    protected Optional<DataEntry> mapCompositeAttribute(AttributePath path, CompositeAttribute compositeAttribute,
            DataEntry inputData) throws InvalidDataException {
        var collector = new ValidationExceptionCollector<>(InvalidDataException.class);
        for (var validator : validators) {
            collector.use(() -> validator.validate(path, compositeAttribute, inputData));
        }
        var mapResult = collector.use(() -> super.mapCompositeAttribute(path, compositeAttribute, inputData));

        collector.rethrow();

        return mapResult;
    }

    @Override
    protected Optional<DataEntry> mapCompositeAttributeUnsupportedDatatype(AttributePath attributePath, CompositeAttribute attribute,
            DataEntry inputData) {
        // The composite attribute will already be validated above, in mapCompositeAttribute
        return Optional.of(inputData);
    }

    public interface ConstraintValidator<T extends Constraint> {

        Class<T> getConstraintType();

        void validate(T constraint, DataEntry dataEntry) throws InvalidDataException;
    }

    public interface Validator {
        void validate(AttributePath attributePath, Attribute attribute, DataEntry dataEntry)
                throws InvalidDataException;
    }

    @RequiredArgsConstructor
    private static class ConstraintValidatorAdapter<T extends Constraint> implements Validator {
        private final ConstraintValidator<T> constraintValidator;

        @Override
        public void validate(AttributePath attributePath, Attribute attribute, DataEntry dataEntry)
                throws InvalidDataException {
            if(attribute instanceof SimpleAttribute simpleAttribute) {
                var maybeConstraint = simpleAttribute.getConstraint(constraintValidator.getConstraintType());
                if(maybeConstraint.isPresent()) {
                    constraintValidator.validate(maybeConstraint.get(), dataEntry);
                }

            }
        }
    }

}
