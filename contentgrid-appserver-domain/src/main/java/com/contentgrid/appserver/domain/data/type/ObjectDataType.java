package com.contentgrid.appserver.domain.data.type;

import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class ObjectDataType implements DataType {

    @NonNull
    private final Set<String> keys;

    public static DataType of(@NonNull CompositeAttribute compositeAttribute) {
        return new ObjectDataType(
                compositeAttribute.getAttributes()
                        .stream()
                        .map(attr -> attr.getName().getValue())
                        .collect(Collectors.toUnmodifiableSet())
        );
    }

    public static DataType of(@NonNull MapDataEntry mapDataEntry) {
        return new ObjectDataType(mapDataEntry.getItems().keySet());
    }

    public static DataType simple() {
        return TechnicalDataType.OBJECT;
    }

    @Override
    public String getTechnicalName() {
        return TechnicalDataType.OBJECT.getTechnicalName();
    }

    @Override
    public String getHumanDescription() {
        return "object with keys %s".formatted(keys.stream().map("'%s'"::formatted).collect(
                Collectors.joining(", ")));
    }
}
