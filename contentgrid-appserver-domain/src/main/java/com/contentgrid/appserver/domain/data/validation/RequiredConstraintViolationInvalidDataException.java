package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.domain.data.InvalidDataException;

public class RequiredConstraintViolationInvalidDataException extends InvalidDataException {

    @Override
    public String getMessage() {
        return "Field is required";
    }
}
