package com.contentgrid.appserver.rest.data;

import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.data.transformers.InvalidDataException;
import com.contentgrid.appserver.domain.data.transformers.InvalidDataFormatException;
import com.contentgrid.appserver.domain.data.type.DataType;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.ConverterNotFoundException;

@RequiredArgsConstructor
public class ConversionServiceRequestInputData implements RequestInputData {
    @NonNull
    private final RequestInputData delegate;

    @NonNull
    private final ConversionService conversionService;

    @Override
    public Stream<String> keys() {
        return delegate.keys();
    }

    @Override
    public DataEntry get(String key, Class<? extends DataEntry> typeHint) throws InvalidDataException {
        var result = delegate.get(key, typeHint);
        try {
            return convert(result, typeHint);
        } catch(ConversionFailedException conversionFailedException) {
            throw new InvalidDataFormatException(DataType.of(typeHint), conversionFailedException.getCause());
        } catch(ConverterNotFoundException converterNotFoundException) {
            return result;
        }
    }

    private DataEntry convert(DataEntry result, Class<? extends DataEntry> typeHint) {
        return Optional.<DataEntry>ofNullable(conversionService.convert(result, typeHint))
                .orElse(NullDataEntry.INSTANCE);
    }

    @Override
    public Result<List<? extends DataEntry>> getList(String key, Class<? extends DataEntry> entryTypeHint)
            throws InvalidDataException {
        var result = delegate.getList(key, entryTypeHint);
        try {
            return result
                    .map(entry -> entry.stream()
                            .map(item -> convert(item, entryTypeHint))
                            .toList()
                    );
        } catch(ConversionFailedException conversionFailedException) {
            throw new InvalidDataFormatException(DataType.of(entryTypeHint), conversionFailedException.getCause());
        } catch(ConverterNotFoundException converterNotFoundException) {
            return result;
        }
    }

    @Override
    public Result<RequestInputData> nested(String key) throws InvalidDataException {
        return delegate.nested(key).map(inputData -> new ConversionServiceRequestInputData(inputData, conversionService));
    }
}
