package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;

/**
 * Transforms any {@link DataEntry} to a different value
 * @param <T> The type of the value to transform to
 */
public interface DataEntryTransformer<T> extends PlainDataEntryTransformer<T>, AnyRelationDataEntryTransformer<T> {

    T transform(FileDataEntry fileDataEntry);
}
