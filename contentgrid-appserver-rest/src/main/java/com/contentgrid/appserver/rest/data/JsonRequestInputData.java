package com.contentgrid.appserver.rest.data;

import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.InstantDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LocalDateDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.ScalarDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidDataFormatException;
import com.contentgrid.appserver.domain.data.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.data.type.DataType;
import com.contentgrid.appserver.domain.data.type.TechnicalDataType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ShortNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JsonRequestInputData implements RequestInputData {
    private final ObjectNode rootNode;
    private final ObjectCodec codec;

    private static final ClassMapping<StringDataEntry, String> STRING_CLASS_MAPPING = new ClassMapping<>(
            StringDataEntry.class, String.class, Set.of(TextNode.class), StringDataEntry::new);
    private static final Map<Class<? extends ScalarDataEntry>, ClassMapping<?, ?>> CLASS_MAPPING = Stream.of(
            new ClassMapping<>(BooleanDataEntry.class, Boolean.class, Set.of(BooleanNode.class), BooleanDataEntry::new),
            new ClassMapping<>(LongDataEntry.class, Long.class, Set.of(IntNode.class, ShortNode.class, LongNode.class, BigIntegerNode.class), LongDataEntry::new),
            new ClassMapping<>(DecimalDataEntry.class, BigDecimal.class, Set.of(NumericNode.class), DecimalDataEntry::new),
            new ClassMapping<>(LocalDateDataEntry.class, LocalDate.class, Set.of(TextNode.class), LocalDateDataEntry::new),
            new ClassMapping<>(InstantDataEntry.class, Instant.class, Set.of(TextNode.class), InstantDataEntry::new)
    ).collect(Collectors.toUnmodifiableMap(ClassMapping::dataEntryClass, Function.identity()));

    record ClassMapping<T extends ScalarDataEntry, U>(
            Class<T> dataEntryClass,
            Class<U> conversionClass,
            Set<Class<? extends JsonNode>> supportedNodeClasses,
            Function<U, T> mapping
    ) {
        DataEntry parse(@NonNull JsonNode node, @NonNull ObjectCodec codec) throws InvalidDataException {
            if(supportedNodeClasses.stream().noneMatch(cls -> cls.isInstance(node))) {
                throw new InvalidDataTypeException(DataType.of(dataEntryClass), nodeToDataType(node));
            }
            try (var parser = node.traverse(codec)){
                var parsedValue = parser.readValueAs(conversionClass);
                if(parsedValue == null) {
                    return NullDataEntry.INSTANCE;
                }

                return mapping.apply(parsedValue);
            } catch (IOException e) {
                throw new InvalidDataFormatException(DataType.of(dataEntryClass), e);
            }

        }

    }

    @Override
    public Stream<String> keys() {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(rootNode.fieldNames(), Spliterator.DISTINCT),
                false
        );
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
        if(node == null || node.isMissingNode()) {
            return MissingDataEntry.INSTANCE;
        }
        if(node.isNull()) {
            return NullDataEntry.INSTANCE;
        }
        if (node.isObject() || node.isArray()) {
            throw new InvalidDataTypeException(DataType.of(typeHint), nodeToDataType(node));
        }

        return CLASS_MAPPING.getOrDefault(typeHint, STRING_CLASS_MAPPING).parse(node, codec);
    }

    @Override
    public Result<RequestInputData> nested(String key) throws InvalidDataException {
        var node = rootNode.get(key);
        return switch (node) {
            case null -> Result.missing();
            case MissingNode missingNode -> Result.missing();
            case NullNode nullNode-> Result.empty();
            case ObjectNode objectNode -> Result.of(new JsonRequestInputData(objectNode, codec));
            default -> throw new InvalidDataTypeException(DataType.of(MapDataEntry.class), nodeToDataType(node));
        };
    }

}
