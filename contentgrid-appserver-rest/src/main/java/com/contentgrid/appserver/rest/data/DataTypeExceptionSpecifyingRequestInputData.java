package com.contentgrid.appserver.rest.data;

import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.InstantDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LocalDateDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidDataFormatException;
import com.contentgrid.appserver.domain.data.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.data.type.DataType;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Wraps a {@link RequestInputData} to try to convert a {@link InvalidDataFormatException} to an {@link InvalidDataTypeException}.
 * in case that is caused by sending data in the format of a different data type.
 */
@RequiredArgsConstructor
public class DataTypeExceptionSpecifyingRequestInputData implements RequestInputData {

    @NonNull
    private final RequestInputData delegate;

    // mapping from requested type to list of incompatible types.
    // If a conversion from the current value to an incompatible type works, an InvalidDataTypeException can be thrown instead of InvalidDataFormatException
    // note that string is not present in these lists, because in case of form submissions (instead of json)
    // all data types start out as strings and are parsed into a more specific type.
    // Adding StringDataEntry to the alternates would suppress a useful InvalidDataFormatException because everything is valid as a string
    // LongDataEntry is always listed before DecimalDataEntry to ensure a more specific error message, as all longs can also be represented as decimals
    private static final Map<Class<? extends DataEntry>, List<Class<? extends DataEntry>>> ALTERNATES = Map.of(
            BooleanDataEntry.class, List.of(LongDataEntry.class, DecimalDataEntry.class),
            LongDataEntry.class, List.of(BooleanDataEntry.class, DecimalDataEntry.class),
            DecimalDataEntry.class, List.of(BooleanDataEntry.class),
            InstantDataEntry.class, List.of(LocalDateDataEntry.class, BooleanDataEntry.class, LongDataEntry.class, DecimalDataEntry.class),
            LocalDateDataEntry.class, List.of(InstantDataEntry.class, BooleanDataEntry.class, LongDataEntry.class, DecimalDataEntry.class)
    );

    @Override
    public Stream<String> keys() {
        return delegate.keys();
    }

    @Override
    public DataEntry get(String key, Class<? extends DataEntry> typeHint) throws InvalidDataException {
        return trySpecify(t -> delegate.get(key, t), typeHint);
    }

    private interface ThrowingFunction<T> {
        T apply(Class<? extends DataEntry> typeHint) throws InvalidDataException;
    }


    @Override
    public Result<List<? extends DataEntry>> getList(String key, Class<? extends DataEntry> entryTypeHint)
            throws InvalidDataException {
        return trySpecify(t -> delegate.getList(key, t), entryTypeHint);
    }

    @Override
    public Result<RequestInputData> nested(String key) throws InvalidDataException {
        return delegate.nested(key);
    }

    private <T> T trySpecify(ThrowingFunction<T> throwingSupplier, Class<? extends DataEntry> typeHint)
            throws InvalidDataException {
        try {
            return throwingSupplier.apply(typeHint);
        } catch(InvalidDataFormatException dataFormatException) {
            // Try conversions to alternate types to throw a more specific
            // data type exception instead of a data format exception
            for (var alternate : ALTERNATES.getOrDefault(typeHint, List.of())) {
                if (canConvert(throwingSupplier, alternate)) {
                    throw new InvalidDataTypeException(DataType.of(typeHint), DataType.of(alternate));
                }
            }
            throw dataFormatException;
        }
    }

    private boolean canConvert(ThrowingFunction<?> throwingSupplier, Class<? extends DataEntry> typeHint) {
        try {
            throwingSupplier.apply(typeHint);
            return true;
        } catch(InvalidDataException dataException) {
            return false;
        }
    }
}
