package com.contentgrid.appserver.rest.data.conversion;

import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import java.math.BigDecimal;
import org.springframework.core.convert.converter.Converter;

public class LongDataEntryToDecimalDataEntryConverter implements Converter<LongDataEntry, DecimalDataEntry> {

    @Override
    public DecimalDataEntry convert(LongDataEntry source) {
        return new DecimalDataEntry(BigDecimal.valueOf(source.getValue()));
    }
}
