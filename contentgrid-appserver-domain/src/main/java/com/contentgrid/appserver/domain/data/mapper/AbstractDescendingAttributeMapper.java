package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.validation.ValidationExceptionCollector;
import java.util.Optional;

/**
 * Abstract base class for attribute mappers that recursively process composite attributes.
 * <p>
 * This mapper traverses attribute hierarchies by handling both simple and composite attributes.
 * For composite attributes, it recursively processes all nested attributes and builds a new
 * composite structure. Subclasses must implement the logic for handling simple attributes
 * and unsupported composite attribute data types.
 */
public abstract class AbstractDescendingAttributeMapper implements AttributeMapper<DataEntry, Optional<DataEntry>> {

    @Override
    public Optional<DataEntry> mapAttribute(Attribute attribute, DataEntry inputData)
            throws InvalidPropertyDataException {
        return mapAttribute(new SimpleAttributePath(attribute.getName()), attribute, inputData);
    }

    protected Optional<DataEntry> mapAttribute(AttributePath path, Attribute attribute, DataEntry inputData)
            throws InvalidPropertyDataException {
        try {
            return switch (attribute) {
                case SimpleAttribute simpleAttribute -> mapSimpleAttribute(path, simpleAttribute, inputData);
                case CompositeAttribute compositeAttribute -> mapCompositeAttribute(path, compositeAttribute, inputData);
            };
        } catch (InvalidDataException e) {
            throw e.withinProperty(attribute.getName());
        }
    }

    protected abstract Optional<DataEntry> mapSimpleAttribute(AttributePath path, SimpleAttribute simpleAttribute, DataEntry inputData)
            throws InvalidDataException;

    protected abstract Optional<DataEntry> mapCompositeAttributeUnsupportedDatatype(AttributePath path, CompositeAttribute attribute,
            DataEntry inputData) throws InvalidDataException;

    protected Optional<DataEntry> mapCompositeAttribute(AttributePath path, CompositeAttribute compositeAttribute, DataEntry inputData)
            throws InvalidDataException {
        if (inputData instanceof MapDataEntry mapDataEntry) {
            var builder = MapDataEntry.builder();
            var collector = new ValidationExceptionCollector<>(InvalidDataException.class);
            for (var attribute : compositeAttribute.getAttributes()) {
                var entry = mapDataEntry.get(attribute.getName().getValue());
                collector.use(() -> mapAttribute(path.withSuffix(attribute.getName()), attribute, entry)
                        .ifPresent(
                                dataEntry -> builder.item(attribute.getName().getValue(), (PlainDataEntry) dataEntry)));
            }
            collector.rethrow();
            return Optional.of(builder.build());
        } else {
            return mapCompositeAttributeUnsupportedDatatype(path, compositeAttribute, inputData);
        }
    }
}
