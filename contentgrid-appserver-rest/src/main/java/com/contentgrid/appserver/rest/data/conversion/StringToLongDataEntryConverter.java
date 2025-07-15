package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.domain.data.DataEntry.InstantDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringToLongDataEntryConverter implements Converter<String, LongDataEntry> {
    private final ConversionService conversionService;

    @Override
    public LongDataEntry convert(String source) {
        var longVal = conversionService.convert(source, Long.class);
        if(longVal == null) {
            return null;
        }
        return new LongDataEntry(longVal);
    }
}
