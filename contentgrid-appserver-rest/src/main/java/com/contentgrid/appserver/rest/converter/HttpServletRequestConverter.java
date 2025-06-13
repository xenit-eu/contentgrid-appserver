package com.contentgrid.appserver.rest.converter;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public interface HttpServletRequestConverter<T> {

    boolean canRead(HttpServletRequest request);

    Optional<T> convert(HttpServletRequest request);
}
