package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;

/**
 * Transforms any {@link com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry} to a different value
 * @param <T> The type of the value to transform to
 */
public interface PlainDataEntryTransformer<T> extends ScalarDataEntryTransformer<T> {

    T transform(ListDataEntry listDataEntry);

    T transform(MapDataEntry mapDataEntry);

    T transform(MissingDataEntry missingDataEntry);
}
