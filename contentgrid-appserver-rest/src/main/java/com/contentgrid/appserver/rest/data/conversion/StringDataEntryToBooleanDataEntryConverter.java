package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.domain.data.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringDataEntryToBooleanDataEntryConverter implements Converter<StringDataEntry, BooleanDataEntry> {

    @Override
    public BooleanDataEntry convert(StringDataEntry source) {
        var bool = switch (source.getValue().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Boolean value must be 'true' or 'false'");
        };
        return new BooleanDataEntry(bool);
    }
}
