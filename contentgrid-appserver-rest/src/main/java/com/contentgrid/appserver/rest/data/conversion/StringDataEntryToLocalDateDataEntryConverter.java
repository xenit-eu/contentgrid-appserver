package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.domain.data.DataEntry.LocalDateDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringDataEntryToLocalDateDataEntryConverter implements Converter<StringDataEntry, LocalDateDataEntry> {

    @Override
    public LocalDateDataEntry convert(StringDataEntry source) {
        var localDate = LocalDate.parse(source.getValue());
        return new LocalDateDataEntry(localDate);
    }
}
