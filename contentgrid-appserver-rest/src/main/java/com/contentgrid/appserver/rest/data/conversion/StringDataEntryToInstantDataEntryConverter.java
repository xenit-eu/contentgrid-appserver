package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.domain.data.DataEntry.InstantDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringDataEntryToInstantDataEntryConverter implements Converter<StringDataEntry, InstantDataEntry> {
    private final ConversionService conversionService;

    @Override
    public InstantDataEntry convert(StringDataEntry source) {
        var instant = conversionService.convert(source.getValue(), Instant.class);
        if(instant == null) {
            return null;
        }
        return new InstantDataEntry(instant);
    }
}
