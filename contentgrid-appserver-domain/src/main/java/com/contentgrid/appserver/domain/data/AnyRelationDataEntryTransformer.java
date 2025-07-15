package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.domain.data.DataEntry.MultipleRelationDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.RelationDataEntry;

/**
 * Transforms any {@link com.contentgrid.appserver.domain.data.DataEntry.AnyRelationDataEntry} to a different value
 * @param <T> The type of the value to transform to
 */
public interface AnyRelationDataEntryTransformer<T> {

    T transform(RelationDataEntry relationDataEntry);

    T transform(MultipleRelationDataEntry multipleRelationDataEntry);

}
