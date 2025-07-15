package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.data.transformers.InvalidPropertyDataException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * Adapter mapper that handles {@linkplain Optional} input types by unwrapping them before delegation.
 * <p>
 * This mapper adapts another {@link AttributeAndRelationMapper} to work with Optional input types.
 * If the input Optional is empty, it returns an empty Optional. Otherwise, it unwraps
 * the input and delegates to the underlying mapper.
 *
 * @param <AT> the unwrapped input data type for attribute mapping
 * @param <AR> the result data type for attribute mapping
 * @param <RT> the unwrapped input data type for relation mapping
 * @param <RR> the result data type for relation mapping
 */
@RequiredArgsConstructor
public class OptionalFlatMapAdaptingMapper<AT, AR, RT, RR> implements AttributeAndRelationMapper<Optional<AT>, Optional<AR>, Optional<RT>, Optional<RR>> {
    private final AttributeAndRelationMapper<AT, Optional<AR>, RT, Optional<RR>> delegate;

    @Override
    public Optional<AR> mapAttribute(Attribute attribute, Optional<AT> inputData) throws InvalidPropertyDataException {
        if(inputData.isEmpty()) {
            return Optional.empty();
        }
        return delegate.mapAttribute(attribute, inputData.get());
    }

    @Override
    public Optional<RR> mapRelation(Relation relation, Optional<RT> inputData) throws InvalidPropertyDataException {
        if(inputData.isEmpty()) {
            return Optional.empty();
        }
        return delegate.mapRelation(relation, inputData.get());
    }
}
