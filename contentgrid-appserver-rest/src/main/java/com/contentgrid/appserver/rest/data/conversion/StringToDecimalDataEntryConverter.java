package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringToDecimalDataEntryConverter implements Converter<String, DecimalDataEntry> {

    private final ConversionService conversionService;
    @Override
    public DecimalDataEntry convert(String source) {
        var decimal = conversionService.convert(source, BigDecimal.class);
        if(decimal == null) {
            return null;
        }
        return new DecimalDataEntry(decimal);
    }
}
