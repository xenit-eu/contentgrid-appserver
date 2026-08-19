package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidDataFormatException;
import com.contentgrid.appserver.domain.data.type.DataType;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.HashSet;
import java.util.List;

/**
 * Enforces the set semantics of a multi-value text attribute: a write containing duplicate elements is
 * rejected, where elements are compared NFKC-normalized (so two encodings of the same character are
 * duplicates), and the number of elements is capped as a robustness guard against unbounded arrays.
 */
public class TextSetValidator implements AttributeValidationDataMapper.Validator {

    public static final int MAX_ELEMENTS = 1000;

    @Override
    public void validate(AttributePath attributePath, Attribute attribute, DataEntry dataEntry)
            throws InvalidDataException {
        if (attribute instanceof SimpleAttribute simpleAttribute
                && simpleAttribute.getType() == Type.TEXT_SET
                && dataEntry instanceof ListDataEntry listDataEntry) {
            validateSet(simpleAttribute, listDataEntry.getItems());
        }
    }

    private static void validateSet(SimpleAttribute attribute, List<PlainDataEntry> items)
            throws InvalidDataException {
        if (items.size() > MAX_ELEMENTS) {
            throw new InvalidDataFormatException(DataType.of(attribute.getType()),
                    new IllegalArgumentException(
                            "A multi-value attribute can contain at most %d elements".formatted(MAX_ELEMENTS)));
        }
        var seen = new HashSet<String>();
        for (var item : items) {
            if (item instanceof StringDataEntry stringDataEntry) {
                if (!seen.add(Normalizer.normalize(stringDataEntry.getValue(), Form.NFKC))) {
                    throw new DuplicateElementInvalidDataException(stringDataEntry.getValue());
                }
            }
        }
    }
}
