package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringDataEntryToLongDataEntryConverter implements Converter<StringDataEntry, LongDataEntry> {
    private final ConversionService conversionService;

    @Override
    public LongDataEntry convert(StringDataEntry source) {
        var longVal = conversionService.convert(source.getValue(), Long.class);
        if(longVal == null) {
            return null;
        }
        return new LongDataEntry(longVal);
    }
}
