package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Combined functional interface that can map both attribute and relation data.
 * <p>
 * This interface extends both {@link AttributeMapper} and {@link RelationMapper} to provide
 * a unified mapping interface that can handle both attributes and relations. This is useful
 * when a single mapper implementation needs to process both types of data.
 *
 * @param <AT> the input data type for attribute mapping
 * @param <AR> the result data type for attribute mapping
 * @param <RT> the input data type for relation mapping
 * @param <RR> the result data type for relation mapping
 */
public interface AttributeAndRelationMapper<AT, AR, RT, RR> extends AttributeMapper<AT, AR>, RelationMapper<RT, RR> {
    /**
     * Creates a composite mapper from separate attribute and relation mappers.
     *
     * @param <AT> the input data type for attribute mapping
     * @param <AR> the result data type for attribute mapping
     * @param <RT> the input data type for relation mapping
     * @param <RR> the result data type for relation mapping
     * @param attributeMapper the mapper for handling attributes
     * @param relationMapper the mapper for handling relations
     * @return a combined mapper that delegates to the provided mappers
     */
    static <AT, AR, RT, RR> AttributeAndRelationMapper<AT, AR, RT, RR> from(
            AttributeMapper<AT, AR> attributeMapper,
            RelationMapper<RT, RR> relationMapper
    ) {
        return new CompositeMapperImpl<>(
                attributeMapper,
                relationMapper
        );
    }
    /**
     * Creates a composite mapper from a single mapper that implements both interfaces.
     *
     * @param <AT> the input data type for attribute mapping
     * @param <AR> the result data type for attribute mapping
     * @param <RT> the input data type for relation mapping
     * @param <RR> the result data type for relation mapping
     * @param <X> the type that implements both AttributeMapper and RelationMapper
     * @param combinedMapper the mapper that handles both attributes and relations
     * @return a combined mapper that delegates to the provided mapper for both operations
     */
    static <AT, AR, RT, RR, X extends AttributeMapper<AT, AR> & RelationMapper<RT, RR>> AttributeAndRelationMapper<AT, AR, RT, RR> from(
            X combinedMapper
    ) {
        return new CompositeMapperImpl<>(
                combinedMapper,
                combinedMapper
        );
    }

    @Override
    default <V> AttributeAndRelationMapper<V, AR, RT, RR> compose(@NonNull AttributeMapper<V, AT> before) {
        return new CompositeMapperImpl<>(
                AttributeMapper.super.compose(before),
                this
        );
    }

    @Override
    default <V> AttributeAndRelationMapper<AT, V, RT, RR> andThen(@NonNull AttributeMapper<AR, V> after) {
        return new CompositeMapperImpl<>(
                AttributeMapper.super.andThen(after),
                this
        );
    }

    @Override
    default <V> AttributeAndRelationMapper<AT, AR, V, RR> compose(@NonNull RelationMapper<V, RT> before) {
        return new CompositeMapperImpl<>(
                this,
                RelationMapper.super.compose(before)
        );
    }

    @Override
    default <V> AttributeAndRelationMapper<AT, AR, RT, V> andThen(@NonNull RelationMapper<RR, V> after) {
        return new CompositeMapperImpl<>(
                this,
                RelationMapper.super.andThen(after)
        );
    }

    /**
     * Returns a composed mapper that first applies the {@code before} mapper to its input,
     * and then applies this mapper to the result for both attributes and relations.
     *
     * @param <AV> the input data type for attribute mapping in the before mapper
     * @param <RV> the input data type for relation mapping in the before mapper
     * @param before the mapper to apply before this mapper is applied
     * @return a composed mapper that first applies the {@code before} mapper and then this mapper
     * @throws NullPointerException if before is null
     */
    default <AV, RV> AttributeAndRelationMapper<AV, AR, RV, RR> compose(@NonNull AttributeAndRelationMapper<AV, AT, RV, RT> before) {
        return before.andThen(this);
    }

    /**
     * Returns a composed mapper that first applies this mapper to its input,
     * and then applies the {@code after} mapper to the result for both attributes and relations.
     *
     * @param <AV> the output data type for attribute mapping in the after mapper
     * @param <RV> the output data type for relation mapping in the after mapper
     * @param after the mapper to apply after this mapper is applied
     * @return a composed mapper that first applies this mapper and then the {@code after} mapper
     * @throws NullPointerException if after is null
     */
    default <AV, RV> AttributeAndRelationMapper<AT, AV, RT, RV> andThen(@NonNull AttributeAndRelationMapper<AR, AV, RR, RV> after) {
        return new CompositeMapperImpl<>(
                AttributeMapper.super.andThen(after),
                RelationMapper.super.andThen(after)
        );
    }
}

/**
 * Internal implementation class that combines separate attribute and relation mappers
 * into a single AttributeAndRelationMapper implementation.
 *
 * @param <AT> the input data type for attribute mapping
 * @param <AR> the result data type for attribute mapping
 * @param <RT> the input data type for relation mapping
 * @param <RR> the result data type for relation mapping
 */
@RequiredArgsConstructor
class CompositeMapperImpl<AT, AR, RT, RR> implements AttributeAndRelationMapper<AT, AR, RT, RR> {
    private final AttributeMapper<AT, AR> attributeMapper;
    private final RelationMapper<RT, RR> relationMapper;

    @Override
    public AR mapAttribute(Attribute attribute, AT inputData) throws InvalidPropertyDataException {
        return attributeMapper.mapAttribute(attribute, inputData);
    }

    @Override
    public RR mapRelation(Relation relation, RT inputData) throws InvalidPropertyDataException {
        return relationMapper.mapRelation(relation, inputData);
    }
}
