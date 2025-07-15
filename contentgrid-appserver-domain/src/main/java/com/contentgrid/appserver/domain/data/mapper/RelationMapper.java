package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.data.transformers.InvalidPropertyDataException;
import lombok.NonNull;

/**
 * Functional interface for mapping relation data from one type to another.
 * <p>
 * A RelationMapper transforms input data of type {@code T} to output data of type {@code R}
 * for a specific relation. This is commonly used in data processing pipelines where relation
 * values need to be transformed, validated, or converted between different representations.
 *
 * @param <T> the input data type
 * @param <R> the result data type after mapping
 */
public interface RelationMapper<T, R> {
    /**
     * Maps the input data for the given relation to the result type.
     *
     * @param relation the relation definition providing metadata about the relation being mapped
     * @param inputData the input data to be mapped
     * @return the mapped result of type R
     * @throws InvalidPropertyDataException if the input data is invalid or cannot be mapped
     */
    R mapRelation(Relation relation, T inputData)
            throws InvalidPropertyDataException;

    /**
     * Returns a composed mapper that first applies the {@code before} mapper to its input,
     * and then applies this mapper to the result.
     *
     * @param <V> the type of input to the {@code before} mapper
     * @param before the mapper to apply before this mapper is applied
     * @return a composed mapper that first applies the {@code before} mapper and then this mapper
     * @throws NullPointerException if before is null
     */
    default <V> RelationMapper<V, R> compose(@NonNull RelationMapper<V, T> before) {
        return before.andThen(this);
    }

    /**
     * Returns a composed mapper that first applies this mapper to its input,
     * and then applies the {@code after} mapper to the result.
     *
     * @param <V> the type of output of the {@code after} mapper
     * @param after the mapper to apply after this mapper is applied
     * @return a composed mapper that first applies this mapper and then the {@code after} mapper
     * @throws NullPointerException if after is null
     */
    default <V> RelationMapper<T, V> andThen(@NonNull RelationMapper<R, V> after) {
        return (relation, inputData) -> after.mapRelation(relation, this.mapRelation(relation, inputData));
    }
}
