package com.contentgrid.appserver.rest.assembler;

import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SequencedMap;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.server.core.Relation;

@Getter
@EqualsAndHashCode(callSuper = true)
@Relation(collectionRelation = IanaLinkRelations.ITEM_VALUE)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EntityDataRepresentationModel extends RepresentationModelWithTemplates<EntityDataRepresentationModel> {

    private final String id;

    @JsonAnyGetter
    private final Map<String, PlainDataEntry> attributes;

    public static EntityDataRepresentationModel from(EntityInstance entityData) {
        return new EntityDataRepresentationModel(
                entityData.getIdentity().getEntityId().getValue().toString(),
                withoutMissingData(entityData.getData())
        );
    }

    private static Map<String, PlainDataEntry> withoutMissingData(SequencedMap<String, PlainDataEntry> data) {
        var map = new LinkedHashMap<>(data);
        var toRemove = map.entrySet()
                .stream()
                .filter(e -> e.getValue() instanceof MissingDataEntry)
                .map(Entry::getKey)
                .collect(Collectors.toSet());
        toRemove.forEach(map::remove);
        return map;
    }

}
