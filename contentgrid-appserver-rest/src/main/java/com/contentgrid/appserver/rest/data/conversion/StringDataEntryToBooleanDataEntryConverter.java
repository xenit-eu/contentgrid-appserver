package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.domain.data.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringDataEntryToBooleanDataEntryConverter implements Converter<StringDataEntry, BooleanDataEntry> {
    private final ConversionService conversionService;

    @Override
    public BooleanDataEntry convert(StringDataEntry source) {
        var bool = conversionService.convert(source.getValue(), Boolean.class);
        if(bool == null) {
            return null;
        }
        return new BooleanDataEntry(bool);
    }
}
