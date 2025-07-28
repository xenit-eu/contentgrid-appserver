package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntryTransformer;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import lombok.RequiredArgsConstructor;

/**
 * Generic mapper that applies a DataEntryTransformer to both attributes and relations.
 * <p>
 * This mapper delegates the actual transformation logic to a {@link DataEntryTransformer},
 * applying the same transformation to both attribute and relation data entries.
 * It's useful for applying consistent transformations across different data types.
 *
 * @param <T> the result type after transformation
 */
@RequiredArgsConstructor
public class TransformingDataEntryMapper<T> implements AttributeAndRelationMapper<DataEntry, T, DataEntry, T> {
    private final DataEntryTransformer<T> transformer;

    @Override
    public T mapAttribute(Attribute attribute, DataEntry inputData)
            throws InvalidPropertyDataException {
        return inputData.map(transformer);
    }

    @Override
    public T mapRelation(Relation relation, DataEntry inputData) throws InvalidPropertyDataException {
        return inputData.map(transformer);
    }
}
