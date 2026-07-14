package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidDataFormatException;
import com.contentgrid.appserver.domain.data.type.DataType;

/**
 * Rejects text values that contain a NUL character ({@code 0x00}).
 * <p>
 * PostgreSQL cannot store {@code 0x00} in {@code text}/{@code varchar} columns (its internals use NUL-terminated
 * C strings), so such a value would otherwise ride down to the driver and surface as an unhandled 500. Rejecting it
 * here turns it into a regular input-validation error (400).
 */
public class NulByteValidator implements AttributeValidationDataMapper.Validator {

    public static final String ERROR_MESSAGE = "Text must not contain the NUL character (0x00)";

    private static final char NUL = '\u0000';

    @Override
    public void validate(AttributePath attributePath, Attribute attribute, DataEntry dataEntry)
            throws InvalidDataException {
        if (attribute instanceof SimpleAttribute simpleAttribute
                && simpleAttribute.getType() == Type.TEXT
                && dataEntry instanceof StringDataEntry stringDataEntry
                && stringDataEntry.getValue().indexOf(NUL) >= 0) {
            throw new InvalidDataFormatException(DataType.of(simpleAttribute.getType()),
                    new IllegalArgumentException(ERROR_MESSAGE));
        }
    }
}
