package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import org.springframework.core.convert.converter.Converter;

public class StringToStringDataEntryConverter implements Converter<String, StringDataEntry> {

    @Override
    public StringDataEntry convert(String source) {
        return new StringDataEntry(source);
    }
}
