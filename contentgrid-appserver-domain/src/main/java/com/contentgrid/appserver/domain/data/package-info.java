/**
 * Core data representation classes
 * <p>
 * This package provides the fundamental data structures and interfaces for representing, transforming,
 * and processing data within the domain layer.
 *
 * <h2>Core Data Structures</h2>
 * <ul>
 *     <li>{@link com.contentgrid.appserver.domain.data.RequestInputData} - Input data for create and update operations, represents the data sent by the user's request</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.DataEntry} - A single data value that is stored in {@link com.contentgrid.appserver.domain.data.RequestInputData}</li>
 * </ul>
 *
 * <h2>Data Entry Types</h2>
 * The {@link com.contentgrid.appserver.domain.data.DataEntry} hierarchy includes:
 * <ul>
 *     <li><strong>PlainDataEntry</strong> - Values representable in structured input (JSON):
 *         <ul>
 *             <li>ScalarDataEntry - Basic types (boolean, decimal, instant, long, null, string)</li>
 *             <li>ListDataEntry - Collections of data entries</li>
 *             <li>MapDataEntry - Key-value structured data</li>
 *             <li>MissingDataEntry - Represents omitted/absent data</li>
 *         </ul>
 *     </li>
 *     <li><strong>AnyRelationDataEntry</strong> - Entity relationships:
 *         <ul>
 *             <li>RelationDataEntry - Single entity reference</li>
 *             <li>MultipleRelationDataEntry - Multiple entity references</li>
 *         </ul>
 *     </li>
 *     <li><strong>FileDataEntry</strong> - File content and metadata</li>
 * </ul>
 *
 * <h2>Data Transformation</h2>
 * <ul>
 *     <li>{@link com.contentgrid.appserver.domain.data.DataEntryTransformer} - Transforms DataEntry to different values</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.PlainDataEntryTransformer} - Specialized for plain data entries</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.ScalarDataEntryTransformer} - Specialized for scalar data entries</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.AnyRelationDataEntryTransformer} - Specialized for relation data entries</li>
 * </ul>
 *
 * <h2>Request Input Data Implementations</h2>
 * <ul>
 *     <li>{@link com.contentgrid.appserver.domain.data.MapRequestInputData} - Map-based implementation</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.UsageTrackingRequestInputData} - Wrapper that tracks data usage</li>
 * </ul>
 *
 * <h2>Exception Hierarchy</h2>
 * <ul>
 *     <li>{@link com.contentgrid.appserver.domain.data.InvalidDataException} - Base exception for data validation errors</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.InvalidDataTypeException} - Type mismatch errors</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.InvalidDataFormatException} - Format/structure errors</li>
 *     <li>{@link com.contentgrid.appserver.domain.data.InvalidPropertyDataException} - Property-specific validation errors</li>
 * </ul>
 *
 * <p>This package is designed around type safety using sealed interfaces, ensuring that all possible
 * data types are known at compile time and can be handled exhaustively through pattern matching
 * and the visitor pattern implemented via transformers.
 *
 * @see com.contentgrid.appserver.domain.data.mapper
 * @see com.contentgrid.appserver.domain.data.type
 * @see com.contentgrid.appserver.domain.data.validation
 */
package com.contentgrid.appserver.domain.data;