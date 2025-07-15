package com.contentgrid.appserver.rest.data;

import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.data.transformers.InvalidDataException;
import com.contentgrid.appserver.domain.data.transformers.InvalidDataFormatException;
import com.contentgrid.appserver.domain.data.transformers.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.type.DataType;
import com.contentgrid.appserver.domain.data.type.TechnicalDataType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RequiredArgsConstructor
public class MultipartRequestInputData implements RequestInputData {
    private final ParameterAccess<String> requestParams;
    private final ParameterAccess<MultipartFile> files;
    private final ConversionService conversionService;

    public static MultipartRequestInputData fromRequest(@NonNull HttpServletRequest servletRequest, @NonNull ConversionService conversionService) {
        if(servletRequest instanceof MultipartHttpServletRequest multipartServletRequest) {
            return new MultipartRequestInputData(
                    new ServletRequestParameterAccess(servletRequest.getParameterMap()),
                    ParameterAccess.forMap(multipartServletRequest.getMultiFileMap()),
                    conversionService
            );
        }
        return new MultipartRequestInputData(
                new ServletRequestParameterAccess(servletRequest.getParameterMap()),
                ParameterAccess.forMap(Map.of()),
                conversionService
        );
    }

    @Override
    public Stream<String> keys() {
        return Stream.concat(
                requestParams.keys(),
                files.keys()
        );
    }

    @Override
    public DataEntry get(String key, Class<? extends DataEntry> typeHint) throws InvalidDataException {
        if(!requestParams.containsKey(key) && !files.containsKey(key)) {
            return MissingDataEntry.INSTANCE;
        }
        var list = convertParams(key, typeHint);

        return switch (list.size()) {
            case 0 -> MissingDataEntry.INSTANCE;
            case 1 -> list.getFirst();
            default -> throw new InvalidDataTypeException(DataType.of(typeHint), TechnicalDataType.LIST);
        };
    }

    @Override
    public Result<List<? extends DataEntry>> getList(String key, Class<? extends DataEntry> entryTypeHint)
            throws InvalidDataException {
        if(!requestParams.containsKey(key) && !files.containsKey(key)) {
            return Result.missing();
        }
        return Result.of(convertParams(key, entryTypeHint));
    }

    private List<? extends DataEntry> convertParams(String key, Class<? extends DataEntry> entryTypeHint)
            throws InvalidDataException{

        if(entryTypeHint == DataEntry.FileDataEntry.class) {
            if(requestParams.containsKey(key)) {
                throw new InvalidDataTypeException(TechnicalDataType.CONTENT, TechnicalDataType.STRING);
            }
            return files.getAll(key).stream()
                    .map(file -> new FileDataEntry(file.getOriginalFilename(), file.getContentType(), file.getSize(), file::getInputStream))
                    .toList();
        }

        // Only FileDataEntry can be derived from files; the rest below can not be derived from files
        // So if we get here, there should be no more files
        if(files.containsKey(key)) {
            throw new InvalidDataTypeException(DataType.of(entryTypeHint), TechnicalDataType.CONTENT);
        }

        if(DataEntry.ScalarDataEntry.class.isAssignableFrom(entryTypeHint)) {
            try {
                return requestParams.getAll(key)
                        .stream()
                        .map(param -> Optional.<DataEntry>ofNullable(conversionService.convert(param, entryTypeHint))
                                .orElse(NullDataEntry.INSTANCE)
                        )
                        .toList();
            } catch(ConversionException e) {
                throw new InvalidDataFormatException(DataType.of(entryTypeHint), e);
            }
        }

        throw new InvalidDataTypeException(DataType.of(entryTypeHint), TechnicalDataType.STRING);
    }

    @Override
    public Result<RequestInputData> nested(String key) throws InvalidDataException {
        if(requestParams.containsKey(key)) {
            throw new InvalidDataTypeException(TechnicalDataType.OBJECT, TechnicalDataType.STRING);
        } else if(files.containsKey(key)) {
            throw new InvalidDataTypeException(TechnicalDataType.OBJECT, TechnicalDataType.CONTENT);
        }

        var inputData = new MultipartRequestInputData(
                new NestedParameterAccess<>(key+".", requestParams),
                new NestedParameterAccess<>(key+".", files),
                conversionService
        );

        if(inputData.keys().findAny().isEmpty()) {
            // If there are no nested parameters, consider the data as missing (instead of being an empty request)
            return Result.missing();
        }

        return Result.of(inputData);
    }

    public interface ParameterAccess<T> {
        Stream<String> keys();
        boolean containsKey(String key);
        List<T> getAll(String key);

        static <T> ParameterAccess<T> forMap(Map<String, List<T>> map) {
            return new MapParameterAccess<>(map);
        }
    }

    private static class MapParameterAccess<T> implements ParameterAccess<T> {

        private final Map<String, List<T>> map;

        public MapParameterAccess(Map<String, List<T>> map) {
            this.map = map;
        }

        @Override
        public Stream<String> keys() {
            return map.keySet().stream();
        }

        @Override
        public boolean containsKey(String key) {
            return map.containsKey(key);
        }

        @Override
        public List<T> getAll(String key) {
            return map.getOrDefault(key, List.of());
        }
    }

    @RequiredArgsConstructor
    private static class NestedParameterAccess<T> implements ParameterAccess<T> {
        private final String prefix;
        private final ParameterAccess<T> original;

        @Override
        public Stream<String> keys() {
            return original.keys()
                    .filter(s -> s.startsWith(prefix))
                    .map(s -> s.substring(prefix.length()));
        }

        @Override
        public boolean containsKey(String key) {
            return original.containsKey(prefix+key);
        }

        @Override
        public List<T> getAll(String key) {
            return original.getAll(prefix+key);
        }
    }

    @RequiredArgsConstructor
    private static class ServletRequestParameterAccess implements ParameterAccess<String> {
        private final Map<String, String[]> data;

        @Override
        public Stream<String> keys() {
            return data.keySet().stream();
        }

        @Override
        public boolean containsKey(String key) {
            return data.containsKey(key);
        }

        @Override
        public List<String> getAll(String key) {
            return List.of(data.getOrDefault(key, new String[0]));
        }
    }
}
