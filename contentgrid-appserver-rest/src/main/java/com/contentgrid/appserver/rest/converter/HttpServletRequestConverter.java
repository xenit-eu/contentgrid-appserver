package com.contentgrid.appserver.rest.converter;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public interface HttpServletRequestConverter<T> {

    /**
     * Returns whether this converter is able to convert the given request.
     * Note: the body InputStream can not yet be consumed.
     *
     * @param request the request to check
     * @return whether this converter is able to convert the given request
     */
    boolean canRead(HttpServletRequest request);

    /**
     * Converts a {@link HttpServletRequest} and returns an {@link Optional} containing the converted request.
     *
     * @param request the request to be converted
     * @return an {@link Optional} containing the converted request, empty if it could not be converted
     */
    Optional<T> convert(HttpServletRequest request);
}
