package com.contentgrid.appserver.rest.converter;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;

@Getter
@RequiredArgsConstructor
public abstract class HttpServletRequestConverter<T> {

    private final List<MediaType> supportedMediaTypes;

    HttpServletRequestConverter(MediaType... supportedMediaTypes) {
        this(List.of(supportedMediaTypes));
    }

    /**
     * Returns whether this converter is able to convert the given media type of the request.
     *
     * @param mediaType the media type of the request to check
     * @return whether this converter is able to convert the given media type of the request
     */
    public boolean canRead(MediaType mediaType) {
        return supportedMediaTypes.stream().anyMatch(supportedMediaType -> supportedMediaType.includes(mediaType));
    }

    /**
     * Converts a {@link HttpServletRequest} and returns an {@link Optional} containing the converted request.
     *
     * @param request the request to be converted
     * @return an {@link Optional} containing the converted request, empty if it could not be converted
     */
    public Optional<T> convert(HttpServletRequest request) {
        try {
            return Optional.ofNullable(read(request));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Converts a {@link HttpServletRequest}.
     *
     * @param request the request to be converted
     * @return the converted request
     */
    protected abstract T read(HttpServletRequest request) throws IOException;
}
