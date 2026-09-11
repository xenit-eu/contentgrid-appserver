package com.contentgrid.appserver.domain.data.type;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class SetDataType implements DataType {

    @NonNull
    private final DataType itemType;

    @Override
    public String getTechnicalName() {
        return itemType.getTechnicalName() + "_set";
    }

    @Override
    public String getHumanDescription() {
        return "set of %s".formatted(itemType.getHumanDescription());
    }

    public static DataType of(@NonNull DataType itemType) {
        return new SetDataType(itemType);
    }

}
