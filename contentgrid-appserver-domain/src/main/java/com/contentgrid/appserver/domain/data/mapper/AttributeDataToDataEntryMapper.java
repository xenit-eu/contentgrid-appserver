package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.domain.data.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.InstantDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

public class AttributeDataToDataEntryMapper implements
        AttributeMapper<Optional<AttributeData>, PlainDataEntry> {

    @Override
    public PlainDataEntry mapAttribute(Attribute attribute, Optional<AttributeData> inputData) {
        return inputData.map(data -> switch (attribute) {
            case SimpleAttribute simpleAttribute -> mapSimpleAttribute(simpleAttribute.getType(), ((SimpleAttributeData<?>)data).getValue());
            case ContentAttribute contentAttribute -> mapContentAttribute(contentAttribute, (CompositeAttributeData)data);
            case UserAttribute userAttribute -> mapUserAttribute(userAttribute, (CompositeAttributeData)data);
            case CompositeAttribute compositeAttribute -> mapCompositeAttribute(compositeAttribute, (CompositeAttributeData)data);
        }).orElse(NullDataEntry.INSTANCE);
    }

    private PlainDataEntry mapUserAttribute(UserAttribute userAttribute, CompositeAttributeData data) {
        return mapAttribute(userAttribute.getUsername(), data.getAttributeByName(userAttribute.getUsername().getName()));
    }

    private PlainDataEntry mapContentAttribute(ContentAttribute contentAttribute, CompositeAttributeData data) {
        var contentId = data.getAttributeByName(contentAttribute.getId().getName());
        if(contentId.isEmpty() || (contentId.get() instanceof SimpleAttributeData<?> simpleAttributeData && simpleAttributeData.getValue() == null)) {
            return NullDataEntry.INSTANCE;
        }

        var allowedAttributes = List.of(
                contentAttribute.getFilename(),
                contentAttribute.getLength(),
                contentAttribute.getMimetype()
        );

        var map = LinkedHashMap.<String, PlainDataEntry>newLinkedHashMap(allowedAttributes.size());

        for (var attribute : allowedAttributes) {
            var value = mapAttribute(attribute, data.getAttributeByName(attribute.getName()));
            map.put(attribute.getName().getValue(), value);
        }

        return new MapDataEntry(map);
    }

    private MapDataEntry mapCompositeAttribute(CompositeAttribute compositeAttribute, CompositeAttributeData data) {
        var map = LinkedHashMap.<String, PlainDataEntry>newLinkedHashMap(compositeAttribute.getAttributes().size());
        for (var attribute : compositeAttribute.getAttributes()) {
            var value = mapAttribute(attribute, data.getAttributeByName(attribute.getName()));
            map.put(attribute.getName().getValue(), value);
        }
        return new MapDataEntry(map);
    }

    private PlainDataEntry mapSimpleAttribute(@NonNull SimpleAttribute.Type type, Object data) {
        if(data == null) {
            return NullDataEntry.INSTANCE;
        }
        return switch (type) {
            case LONG -> new LongDataEntry((Long) data);
            case DOUBLE -> new DecimalDataEntry((BigDecimal) data);
            case BOOLEAN -> new BooleanDataEntry((Boolean) data);
            case TEXT, UUID -> new StringDataEntry(data.toString());
            case DATETIME -> new InstantDataEntry((Instant) data);
        };
    }

}
