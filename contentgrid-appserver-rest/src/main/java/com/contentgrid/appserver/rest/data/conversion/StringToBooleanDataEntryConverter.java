package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.domain.data.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringToBooleanDataEntryConverter implements Converter<String, BooleanDataEntry> {
    private final ConversionService conversionService;

    @Override
    public BooleanDataEntry convert(String source) {
        var bool = conversionService.convert(source, Boolean.class);
        if(bool == null) {
            return null;
        }
        return new BooleanDataEntry(bool);
    }
}
