package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
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

    Map<HttpStatusCode, OpenApiResponse> responses = new LinkedHashMap<>();

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

    public OpenApiResponse getResponse(int statusCode) {
        return responses.get(HttpStatusCode.of(statusCode));
    }

    public OpenApiOperation eachResponse(BiConsumer<HttpStatusCode, OpenApiResponse> responseConsumer) {
        responses.forEach(responseConsumer);
        return this;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @EqualsAndHashCode
    @ToString(includeFieldNames = false)
    public static class HttpStatusCode {
        @JsonValue
        @NonNull
        private final String code;

        public static final HttpStatusCode DEFAULT = new HttpStatusCode("default");

        public static HttpStatusCode of(int statusCode) {
            return new HttpStatusCode(Integer.toString(statusCode));
        }

        public boolean isSuccess() {
            return code.startsWith("2");
        }

        public boolean isRedirect() {
            return code.startsWith("3");
        }

        public boolean isClientError() {
            return code.startsWith("4");
        }

        public boolean isServerError() {
            return code.startsWith("5");
        }

        public boolean isError() {
            return isClientError() || isServerError();
        }
    }
}
