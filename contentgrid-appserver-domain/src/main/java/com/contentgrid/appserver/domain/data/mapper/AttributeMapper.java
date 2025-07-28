package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import lombok.NonNull;

/**
 * Functional interface for mapping attribute data from one type to another.
 * <p>
 * An AttributeMapper transforms input data of type {@code T} to output data of type {@code R}
 * for a specific attribute. This is commonly used in data processing pipelines where attribute
 * values need to be transformed, validated, or converted between different representations.
 *
 * @param <T> the input data type
 * @param <R> the result data type after mapping
 */
public interface AttributeMapper<T, R> {
    /**
     * Maps the input data for the given attribute to the result type.
     *
     * @param attribute the attribute definition providing metadata about the attribute being mapped
     * @param inputData the input data to be mapped
     * @return the mapped result of type R
     * @throws InvalidPropertyDataException if the input data is invalid or cannot be mapped
     */
    R mapAttribute(Attribute attribute, T inputData)
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
    default <V> AttributeMapper<V, R> compose(@NonNull AttributeMapper<V, T> before) {
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
    default <V> AttributeMapper<T, V> andThen(@NonNull AttributeMapper<R, V> after) {
        return (attribute, inputData) -> after.mapAttribute(attribute, this.mapAttribute(attribute, inputData));
    }
}
