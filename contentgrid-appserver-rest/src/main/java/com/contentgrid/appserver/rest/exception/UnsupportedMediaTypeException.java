package com.contentgrid.appserver.rest.exception;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

public class UnsupportedMediaTypeException extends UnsupportedMediaTypeStatusException {

    public UnsupportedMediaTypeException(MediaType mediaType, List<MediaType> supportedMediaTypes) {
        super(mediaType, supportedMediaTypes);
    }

}
