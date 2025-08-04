package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.validation.AttributeValidationDataMapper.ConstraintValidator;

public class RequiredAttributeConstraintValidator implements ConstraintValidator<RequiredConstraint> {

    @Override
    public Class<RequiredConstraint> getConstraintType() {
        return RequiredConstraint.class;
    }

    @Override
    public void validate(RequiredConstraint constraint, DataEntry dataEntry) throws InvalidDataException {
        if (dataEntry instanceof NullDataEntry || dataEntry instanceof MissingDataEntry) {
            throw new RequiredConstraintViolationInvalidDataException();
        }
    }
}
