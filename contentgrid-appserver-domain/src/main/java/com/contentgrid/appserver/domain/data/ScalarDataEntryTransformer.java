package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.domain.data.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.InstantDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;

/**
 * Transforms any {@link com.contentgrid.appserver.domain.data.DataEntry.ScalarDataEntry} to a different value
 * @param <T> The type of the value to transform to
 */
public interface ScalarDataEntryTransformer<T> {

    T transform(StringDataEntry stringDataEntry);

    T transform(LongDataEntry numberDataEntry);

    T transform(DecimalDataEntry decimalDataEntry);

    T transform(BooleanDataEntry booleanDataEntry);

    T transform(NullDataEntry nullDataEntry);

    T transform(InstantDataEntry instantDataEntry);
}
