package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringDataEntryToDecimalDataEntryConverter implements Converter<StringDataEntry, DecimalDataEntry> {

    @Override
    public DecimalDataEntry convert(StringDataEntry source) {
        var decimal = new BigDecimal(source.getValue());
        return new DecimalDataEntry(decimal);
    }
}
