package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.ScalarDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.type.DataType;
import com.contentgrid.appserver.domain.data.validation.AttributeValidationDataMapper.ConstraintValidator;
import java.util.Objects;

public class AllowedValuesConstraintValidator implements ConstraintValidator<AllowedValuesConstraint> {

    @Override
    public Class<AllowedValuesConstraint> getConstraintType() {
        return AllowedValuesConstraint.class;
    }

    @Override
    public void validate(AllowedValuesConstraint constraint, DataEntry dataEntry) throws InvalidDataException {
        if(dataEntry instanceof NullDataEntry || dataEntry instanceof MissingDataEntry) {
            // Validator does not check empty or null values, they are allowed
            return;
        }
        if(dataEntry instanceof ScalarDataEntry scalarDataEntry) {
            var stringValue = Objects.toString(scalarDataEntry.getValue());
            if(constraint.getValues().contains(stringValue)) {
                // This is a valid value, return without throwing
                return;
            }
            throw new AllowedValuesConstraintViolationInvalidDataException(stringValue, constraint.getValues());
        }
        if(dataEntry instanceof ListDataEntry listDataEntry) {
            // A multi-value attribute is valid when every element is an allowed value
            for (var item : listDataEntry.getItems()) {
                validate(constraint, item);
            }
            return;
        }

        throw new IllegalArgumentException(
                "%s does not support %s".formatted(
                        AllowedValuesConstraintValidator.class,
                        DataType.of(dataEntry))
        );


    }
}
