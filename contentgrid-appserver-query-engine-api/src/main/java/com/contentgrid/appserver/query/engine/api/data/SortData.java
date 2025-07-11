package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.SortableName;
import java.util.List;
import lombok.Value;

@Value
public class SortData {
    List<FieldSort> sortedFields;

    @Value
    public static class FieldSort {
        Direction direction;
        SortableName name;
    }

    public enum Direction { ASC, DESC }
}
