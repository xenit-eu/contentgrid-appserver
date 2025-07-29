/**
 * Data transformation mappers for converting attributes and relations between different data representations.
 * <p>
 * This package provides functional interfaces and implementations for mapping attributes and relation data between
 * different types. The mappers support both attributes and relations, enabling type-safe transformation, validation, and conversion of data.
 *
 * <h2>Core Mapper Interfaces</h2>
 * <ul>
 *     <li>{@link com.contentgrid.appserver.domain.data.mapper.AttributeMapper} - Maps attribute data from one type to another</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.mapper.RelationMapper} - Maps relation data from one type to another</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.mapper.AttributeAndRelationMapper} - Combined interface for handling both attributes and relations</li>
 * </ul>
 *
 * <h2>Concrete Implementations</h2>
 * <ul>
 *     <li>{@link com.contentgrid.appserver.domain.data.mapper.RequestInputDataToDataEntryMapper} - Converts request input data to domain data entries</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.mapper.DataEntryToQueryEngineMapper} - Converts domain data entries to query engine format</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.mapper.TransformingDataEntryMapper} - Applies transformations to data entries</li>
 * </ul>
 *
 * <h2>Utility Classes</h2>
 * <ul>
 *     <li>{@link com.contentgrid.appserver.domain.data.mapper.AbstractDescendingAttributeMapper} - Base class for composite attribute mapping</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.mapper.OptionalFlatMapAdaptingMapper} - Adapter for optional flat mapping operations</li>
 * </ul>
 *
 * <p>All mappers in this package are designed to be composable, allowing complex data
 * transformation pipelines to be built by chaining simpler mappers together using the
 * {@code compose} and {@code andThen} methods.
 *
 * @see com.contentgrid.appserver.domain.DatamodelApiImpl DatamodelApiImpl where the mappers are composed into a transformation pipeline from {@link com.contentgrid.appserver.domain.data.RequestInputData} to {@link com.contentgrid.appserver.query.engine.api.data.AttributeData} and {@link com.contentgrid.appserver.query.engine.api.data.RelationData}
 */
package com.contentgrid.appserver.domain.data.mapper;