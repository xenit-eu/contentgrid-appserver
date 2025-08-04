package com.contentgrid.appserver.rest.converter;

import com.contentgrid.appserver.rest.converter.UriListHttpMessageConverter.URIList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;

@Component
public class UriListHttpMessageConverter extends AbstractHttpMessageConverter<URIList> {

    public UriListHttpMessageConverter() {
        super(MediaType.parseMediaType("text/uri-list"));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return URIList.class.isAssignableFrom(clazz);
    }

    @Override
    protected URIList readInternal(Class<? extends URIList> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputMessage.getBody(), StandardCharsets.UTF_8))) {
            return new URIList(reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#")) // Ignore empty lines and comments
                    .map(line -> {
                        try {
                            return URI.create(line);
                        } catch (IllegalArgumentException e) {
                            throw new HttpMessageNotReadableException("Invalid URI in text/uri-list: " + line, e, inputMessage);
                        }
                    })
                    .toList());
        }
    }

    @Override
    protected void writeInternal(URIList list, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        for (URI uri : list.uris()) {
            outputMessage.getBody().write((uri.toASCIIString() + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    public record URIList(List<URI> uris) {}
}