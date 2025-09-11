package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;

@Value
class InternalEntityInstance implements EntityInstance {
    EntityIdentity identity;
    SequencedMap<String, PlainDataEntry> data;
    @Getter(value = AccessLevel.NONE)
    List<AttributeData> attributeData;

    /* package-private */ Optional<AttributeData> getByAttributeName(AttributeName attributeName) {
        for (var attributeDatum : attributeData) {
            if(Objects.equals(attributeDatum.getName(), attributeName)) {
                return Optional.of(attributeDatum);
            }
        }
        return Optional.empty();
    }

    /* package-private */ <T extends AttributeData> Optional<T> getByAttributeName(AttributeName attributeName, Class<T> expectedType) {
        return getByAttributeName(attributeName)
                .map(expectedType::cast);
    }
}
