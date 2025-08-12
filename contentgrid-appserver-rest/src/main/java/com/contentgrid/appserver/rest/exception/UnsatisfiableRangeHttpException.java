package com.contentgrid.appserver.rest.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class UnsatisfiableRangeHttpException extends ResponseStatusException {
    private final long resourceLength;

    public UnsatisfiableRangeHttpException(long resourceLength) {
        super(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        this.resourceLength = resourceLength;
    }

    @Override
    public HttpHeaders getHeaders() {
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_RANGE, "bytes */"+resourceLength);
        return headers;
    }
}
