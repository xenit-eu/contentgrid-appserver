package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.application.model.propertypath.SimpleAttributePath;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.validation.ValidationExceptionCollector;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SingleDataEntryToDataEntryMapper implements AttributeMapper<DataEntry, Optional<DataEntry>> {

    @NonNull
    private final AttributePath targetAttribute;

    @Override
    public Optional<DataEntry> mapAttribute(Attribute attribute, DataEntry inputData)
            throws InvalidPropertyDataException {
        return mapAttribute(new SimpleAttributePath(attribute.getName()), attribute, inputData);
    }

    protected Optional<DataEntry> mapAttribute(AttributePath path, Attribute attribute, DataEntry inputData)
            throws InvalidPropertyDataException {
        if (targetAttribute.equals(path)) {
            return Optional.of(inputData);
        }
        try {
            if (attribute instanceof CompositeAttribute compositeAttribute) {
                return mapCompositeAttribute(path, compositeAttribute, inputData);
            }
            return Optional.empty();
        } catch (InvalidDataException e) {
            throw e.withinProperty(attribute.getName());
        }
    }



    protected Optional<DataEntry> mapCompositeAttribute(AttributePath path, CompositeAttribute compositeAttribute, DataEntry inputData)
            throws InvalidDataException {
        var builder = MapDataEntry.builder();
        var collector = new ValidationExceptionCollector<>(InvalidDataException.class);
        for (var attribute : compositeAttribute.getAttributes()) {
            collector.use(() -> mapAttribute(path.withSuffix(attribute.getName()), attribute, inputData)
                    .ifPresent(
                            dataEntry -> builder.item(attribute.getName().getValue(), dataEntry)));
        }
        collector.rethrow();
        return Optional.of(builder.build());
    }
}
