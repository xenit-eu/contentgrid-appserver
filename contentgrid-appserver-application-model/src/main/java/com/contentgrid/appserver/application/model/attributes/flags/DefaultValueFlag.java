package com.contentgrid.appserver.application.model.attributes.flags;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import com.contentgrid.appserver.application.model.values.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.InstantDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.LocalDateDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.LongDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.ScalarDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.StringDataEntry;
import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
public class DefaultValueFlag implements AttributeFlag {
    @Override
    public void checkSupported(Attribute attribute) {
        if (attribute instanceof SimpleAttribute simp) {

            // TODO
            var neededDataEntry = switch(simp.getType()) {
                case LONG -> LongDataEntry.class;
                case DOUBLE -> DecimalDataEntry.class;
                case BOOLEAN -> BooleanDataEntry.class;
                case TEXT, UUID -> StringDataEntry.class;
                case DATE -> LocalDateDataEntry.class;
                case DATETIME -> InstantDataEntry.class;
            };

            if (!neededDataEntry.isAssignableFrom(defaultValue.getClass())) {
                throw new InvalidFlagException("Invalid type");
            }
        } else {
            throw new InvalidFlagException("No composites for default values");
        }

    }

    @NonNull
    ScalarDataEntry defaultValue;
}
