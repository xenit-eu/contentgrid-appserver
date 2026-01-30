package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.application.model.values.DataEntry.LocalDateDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.StringDataEntry;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringDataEntryToLocalDateDataEntryConverter implements Converter<StringDataEntry, LocalDateDataEntry> {
    private final ConversionService conversionService;

    @Override
    public LocalDateDataEntry convert(StringDataEntry source) {
        var localDate = conversionService.convert(source.getValue(), LocalDate.class);
        if (localDate == null) {
            return null;
        }
        return new LocalDateDataEntry(localDate);
    }
}
