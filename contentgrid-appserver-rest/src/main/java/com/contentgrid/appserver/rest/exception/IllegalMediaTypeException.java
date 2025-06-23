package com.contentgrid.appserver.rest.exception;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

public class IllegalMediaTypeException extends UnsupportedMediaTypeStatusException {

    public IllegalMediaTypeException(String mediaType, List<MediaType> supportedMediaTypes) {
        super("Media type %s could not be parsed".formatted(mediaType), supportedMediaTypes);
    }

}
