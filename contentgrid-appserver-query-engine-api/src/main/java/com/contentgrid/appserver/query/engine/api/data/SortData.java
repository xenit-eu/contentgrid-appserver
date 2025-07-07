package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.AttributeName;
import java.util.List;
import lombok.Value;

@Value
public class SortData {
    List<FieldSort> sortedFields;

    @Value
    public static class FieldSort {
        Direction direction;
        AttributeName attributeName;
    }

    public enum Direction { ASC, DESC }
}
