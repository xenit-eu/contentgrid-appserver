package com.contentgrid.appserver.rest.converter;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
public class UriListHttpServletRequestConverter extends AbstractHttpServletRequestConverter<List<URI>> {

    public UriListHttpServletRequestConverter() {
        super(MediaType.parseMediaType("text/uri-list"));
    }

    @Override
    public List<URI> read(HttpServletRequest request)
            throws IOException, HttpMessageNotReadableException {
        try (BufferedReader reader = request.getReader()) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#")) // Ignore empty lines and comments
                    .map(line -> {
                        try {
                            return URI.create(line);
                        } catch (IllegalArgumentException e) {
                            throw new HttpMessageNotReadableException("Invalid URI in text/uri-list: " + line, e,
                                    new ServletServerHttpRequest(request));
                        }
                    })
                    .toList();
        }
    }
}
