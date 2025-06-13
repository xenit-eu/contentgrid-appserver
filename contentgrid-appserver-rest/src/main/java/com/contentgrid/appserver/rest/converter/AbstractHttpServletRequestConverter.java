package com.contentgrid.appserver.rest.converter;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;

@RequiredArgsConstructor
public abstract class AbstractHttpServletRequestConverter<T> implements HttpServletRequestConverter<T> {

    private final List<MediaType> supportedMediaTypes;

    AbstractHttpServletRequestConverter(MediaType... supportedMediaTypes) {
        this(List.of(supportedMediaTypes));
    }

    @Override
    public boolean canRead(HttpServletRequest request) {
        var mediaType = MediaType.parseMediaType(request.getContentType());
        return supportedMediaTypes.stream().anyMatch(supportedMediaType -> supportedMediaType.includes(mediaType));
    }

    @Override
    public Optional<T> convert(HttpServletRequest request) {
        try {
            return Optional.of(read(request));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    protected abstract T read(HttpServletRequest request) throws IOException;
}
