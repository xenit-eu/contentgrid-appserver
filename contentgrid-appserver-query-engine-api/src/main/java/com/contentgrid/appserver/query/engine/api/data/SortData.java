package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.SortableName;
import java.util.List;
import lombok.NonNull;
import lombok.Value;

@Value
public class SortData {
    @NonNull
    List<FieldSort> sortedFields;

    @Value
    public static class FieldSort {
        @NonNull
        Direction direction;
        @NonNull
        SortableName name;

        @Override
        public String toString() {
            return name.getValue() + "," + direction.name().toLowerCase();
        }
    }

    public enum Direction { ASC, DESC }

    public List<String> toList() {
        return sortedFields.stream().map(Object::toString).toList();
    }

    public static SortData unsorted() {
        return new SortData(List.of());
    }
}
