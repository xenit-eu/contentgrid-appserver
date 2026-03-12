package com.contentgrid.appserver.rest.converter;

import com.contentgrid.appserver.rest.converter.UriListHttpMessageConverter.URIList;
import com.contentgrid.appserver.rest.exception.InvalidUriInListException;
import jakarta.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    protected URIList readInternal(@Nonnull Class<? extends URIList> clazz, @Nonnull HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        List<URI> uris = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputMessage.getBody(), StandardCharsets.UTF_8))) {
            String line;
            long lineNumber = 0;
            while((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.startsWith("#") || line.isEmpty()) { // Ignore comments and empty lines
                    continue;
                }

                try {
                    uris.add(new URI(line));
                } catch (URISyntaxException e) {
                    throw new InvalidUriInListException(lineNumber, e, inputMessage);
                }

            }
        }
        return new URIList(uris);
    }

    @Override
    protected void writeInternal(URIList list, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        for (URI uri : list.uris()) {
            outputMessage.getBody().write(uri.toString().getBytes(StandardCharsets.UTF_8));
            outputMessage.getBody().write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    public record URIList(List<URI> uris) {}
}