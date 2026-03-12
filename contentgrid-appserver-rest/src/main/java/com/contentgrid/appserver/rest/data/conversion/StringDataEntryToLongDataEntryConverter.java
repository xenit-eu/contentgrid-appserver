package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringDataEntryToLongDataEntryConverter implements Converter<StringDataEntry, LongDataEntry> {

    @Override
    public LongDataEntry convert(StringDataEntry source) {
        // Variant with CharSequence is used because it gives the position of the error in the thrown exception
        var longVal = Long.parseLong(source.getValue(), 0, source.getValue().length(), 10);
        return new LongDataEntry(longVal);
    }
}
