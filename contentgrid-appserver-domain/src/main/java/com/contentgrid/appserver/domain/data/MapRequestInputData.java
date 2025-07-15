package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.InstantDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.transformers.InvalidDataException;
import com.contentgrid.appserver.domain.data.transformers.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.type.DataType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Input data based on a simple map as input.
 * <p>
 * Values of the map can either be plain Java types that map to a supported {@link DataEntry},
 * or can directly be instances of {@link DataEntry}
 */
@RequiredArgsConstructor
public class MapRequestInputData implements RequestInputData {
    @NonNull
    private final Map<String, ? extends DataEntry> data;

    public static RequestInputData fromMap(Map<String, Object> map) {
        return new MapRequestInputData(
                map.entrySet()
                        .stream()
                        .map(entry -> Map.entry(entry.getKey(), convertValue(entry.getValue())))
                        .collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue))
        );
    }

    private static DataEntry convertValue(Object value) {
        return switch (value) {
            case null -> NullDataEntry.INSTANCE;
            case Short shortVal -> new LongDataEntry(shortVal.longValue());
            case Integer intValue -> new LongDataEntry(intValue.longValue());
            case Long longValue -> new LongDataEntry(longValue);
            case Float floatVal -> new DecimalDataEntry(BigDecimal.valueOf(floatVal));
            case Double doubleVal -> new DecimalDataEntry(BigDecimal.valueOf(doubleVal));
            case BigDecimal bigDecimal -> new DecimalDataEntry(bigDecimal);
            case String s -> new StringDataEntry(s);
            case Instant instant -> new InstantDataEntry(instant);
            case List<?> list -> new ListDataEntry(list.stream()
                    .map(MapRequestInputData::convertValue)
                    .map(PlainDataEntry.class::cast)
                    .toList());
            case Map<?, ?> map -> new MapDataEntry(map.entrySet().stream()
                    .map(entry -> Map.entry((String)entry.getKey(), (PlainDataEntry)convertValue(entry.getValue())))
                    .collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue))
            );
            default -> throw new IllegalArgumentException("Unsupported value "+value.getClass());
        };
    }

    @Override
    public Stream<String> keys() {
        return data.keySet().stream();
    }

    @Override
    public DataEntry get(String key, Class<? extends DataEntry> typeHint) throws InvalidDataException {
        var value = data.get(key);
        if(value == null) {
            return MissingDataEntry.INSTANCE;
        }
        return value;
    }

    @Override
    public Result<List<? extends DataEntry>> getList(String key, Class<? extends DataEntry> entryTypeHint)
            throws InvalidDataException {
        var dataEntry = get(key, ListDataEntry.class);
        return switch (dataEntry) {
            case MissingDataEntry ignored -> Result.missing();
            case NullDataEntry ignored -> Result.empty();
            case ListDataEntry listDataEntry -> Result.of(listDataEntry.getItems());
            default -> throw new InvalidDataTypeException(DataType.of(ListDataEntry.class), DataType.of(dataEntry));
        };
    }

    @Override
    public Result<RequestInputData> nested(String key) throws InvalidDataException {
        var dataEntry = get(key, MapDataEntry.class);
        return switch (dataEntry) {
            case MissingDataEntry ignored -> Result.missing();
            case NullDataEntry ignored -> Result.empty();
            case MapDataEntry mapDataEntry -> Result.of(new MapRequestInputData(mapDataEntry.getItems()));
            default -> throw new InvalidDataTypeException(DataType.of(MapDataEntry.class), DataType.of(dataEntry));
        };
    }
}
