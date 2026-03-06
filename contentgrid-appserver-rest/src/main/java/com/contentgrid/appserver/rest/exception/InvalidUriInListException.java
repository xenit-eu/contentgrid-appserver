package com.contentgrid.appserver.rest.exception;

import java.net.URISyntaxException;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;

public class InvalidUriInListException extends HttpMessageNotReadableException {
    @Getter
    private final long lineNumber;

    public InvalidUriInListException(long lineNumber, @NonNull URISyntaxException cause, @NonNull HttpInputMessage inputMessage) {
        super("Invalid URI at line %s: %s".formatted(lineNumber, cause.getReason()), cause, inputMessage);
        this.lineNumber = lineNumber;
    }

    @Override
    public synchronized URISyntaxException getCause() {
        return (URISyntaxException) super.getCause();
    }

}
