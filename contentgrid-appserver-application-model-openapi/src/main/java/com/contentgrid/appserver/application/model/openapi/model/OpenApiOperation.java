package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Data
@Accessors(chain = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OpenApiOperation {
    List<String> tags;
    String summary;
    String description;
    String operationId;

    List<OpenApiParameter> parameters;

    OpenApiRequestBody requestBody;

    Map<HttpStatusCode, OpenApiResponse> responses;

    public OpenApiOperation requestBody(Consumer<OpenApiRequestBody> requestBodyConsumer) {
        requestBody = new OpenApiRequestBody();
        requestBodyConsumer.accept(requestBody);
        return this;
    }

    public OpenApiOperation response(int statusCode, Consumer<OpenApiResponse> responseConsumer) {
        var resp = new OpenApiResponse();
        responseConsumer.accept(resp);
        responses.put(HttpStatusCode.of(statusCode), resp);
        return this;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class HttpStatusCode {
        @JsonValue
        @NonNull
        private final String code;

        public static final HttpStatusCode DEFAULT = new HttpStatusCode("default");

        public static HttpStatusCode of(int statusCode) {
            return new HttpStatusCode(Integer.toString(statusCode));
        }
    }
}
