package com.contentgrid.appserver.events;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.data.mapper.AttributeDataToDataEntryMapper;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventMapper {
    private final ObjectMapper objectMapper;

    public EntityChangeEventPayload map(EntityChangeEvent event) {
        var oldNode = event.getOldData().map(data -> toJson(event.getApplication(), event.getEntity(), data)).orElse(null);
        var newNode = event.getNewData().map(data -> toJson(event.getApplication(), event.getEntity(), data)).orElse(null);
        return new EntityChangeEventPayload(event.getTrigger().getValue(), oldNode, newNode);
    }

    private JsonNode toJson(Application application, EntityName entityName, EntityData data) {
        Entity entity = application.getRequiredEntityByName(entityName);
        var mapper = new AttributeDataToDataEntryMapper();

        var attributes = new LinkedHashMap<String, PlainDataEntry>(entity.getAttributes().size());
        entity.getAttributes().forEach(attr -> {
            Optional<AttributeData> maybeData = data.getAttributeByName(attr.getName());
            var mapped = mapper.mapAttribute(attr, maybeData);
            attributes.put(attr.getName().getValue(), mapped);
        });

        EntityInstance instance = new SimpleEntityInstance(data.getIdentity(), attributes);
//        var model = EntityDataRepresentationModel.from(instance);
//        return objectMapper.valueToTree(model);
        return objectMapper.valueToTree(instance);
    }

    @Value
    private static class SimpleEntityInstance implements EntityInstance {
        EntityIdentity identity;
        SequencedMap<String, PlainDataEntry> data;
//        List<AttributeData> attributes;
    }
}

