package com.contentgrid.appserver.rest.data;

import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.data.type.DataType;
import com.contentgrid.appserver.domain.data.type.TechnicalDataType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JsonRequestInputData implements RequestInputData {
    private final ObjectNode rootNode;

    @Override
    public Stream<String> keys() {
        return rootNode.propertyNames().stream();
    }

    @Override
    public DataEntry get(String key, Class<? extends DataEntry> typeHint) throws InvalidDataException {
        return convertNode(rootNode.get(key), typeHint);
    }

    @Override
    public Result<List<? extends DataEntry>> getList(String key, Class<? extends DataEntry> entryTypeHint) throws InvalidDataException {
        var node = rootNode.get(key);
        return switch (node) {
            case null -> Result.missing();
            case MissingNode missingNode -> Result.missing();
            case NullNode nullNode-> Result.empty();
            case ArrayNode arrayNode -> {
                var entries = new ArrayList<DataEntry>(arrayNode.size());
                for (var entry : arrayNode) {
                    entries.add(convertNode(entry, entryTypeHint));
                }
                yield Result.of(entries);
            }
            default -> throw new InvalidDataTypeException(DataType.of(ListDataEntry.class), nodeToDataType(node));
        };
    }

    private static DataType nodeToDataType(JsonNode node) {
        return switch (node.getNodeType()) {
            case ARRAY -> TechnicalDataType.LIST;
            case BOOLEAN -> TechnicalDataType.BOOLEAN;
            case MISSING -> TechnicalDataType.MISSING;
            case NULL -> TechnicalDataType.NULL;
            case NUMBER -> switch (node.numberType()) {
                case INT, LONG, BIG_INTEGER -> TechnicalDataType.LONG;
                case FLOAT, DOUBLE, BIG_DECIMAL -> TechnicalDataType.DECIMAL;
            };
            case POJO, OBJECT -> TechnicalDataType.OBJECT;
            case BINARY, STRING -> TechnicalDataType.STRING;
        };
    }

    private DataEntry convertNode(JsonNode node, Class<? extends DataEntry> typeHint)
            throws InvalidDataException {
        if(node == null) {
            return MissingDataEntry.INSTANCE;
        }

        return switch (node.getNodeType()) {
            case POJO, OBJECT, ARRAY -> throw new InvalidDataTypeException(DataType.of(typeHint), nodeToDataType(node));
            case BOOLEAN -> new BooleanDataEntry(node.booleanValue());
            case MISSING -> MissingDataEntry.INSTANCE;
            case NULL -> NullDataEntry.INSTANCE;
            case NUMBER -> switch (node.numberType()) {
                case INT, LONG, BIG_INTEGER -> new LongDataEntry(node.longValue());
                case FLOAT, DOUBLE, BIG_DECIMAL -> new DecimalDataEntry(node.decimalValue());
            };
            case BINARY, STRING -> new StringDataEntry(node.textValue());
        };
    }

    @Override
    public Result<RequestInputData> nested(String key) throws InvalidDataException {
        var node = rootNode.get(key);
        return switch (node) {
            case null -> Result.missing();
            case MissingNode missingNode -> Result.missing();
            case NullNode nullNode-> Result.empty();
            case ObjectNode objectNode -> Result.of(new JsonRequestInputData(objectNode));
            default -> throw new InvalidDataTypeException(DataType.of(MapDataEntry.class), nodeToDataType(node));
        };
    }

}
