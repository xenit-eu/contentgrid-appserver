package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Data
@Accessors(chain = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OpenApiOperation {
    @JsonInclude(Include.NON_EMPTY)
    final Set<String> tags = new TreeSet<>();
    @JsonInclude(Include.NON_EMPTY)
    String operationId;
    @JsonInclude(Include.NON_EMPTY)
    String summary;
    @JsonInclude(Include.NON_EMPTY)
    String description;

    @JsonInclude(Include.NON_EMPTY)
    final Set<OpenApiPotentialReference<OpenApiParameter>> parameters = new TreeSet<>(Comparator.comparing(OpenApiPotentialReference::getOriginalObject));

    @JsonInclude(Include.NON_EMPTY)
    OpenApiRequestBody requestBody;

    @JsonInclude(Include.NON_EMPTY)
    Map<HttpStatusCode, OpenApiPotentialReference<OpenApiResponse>> responses = new TreeMap<>(Comparator.comparing(HttpStatusCode::toString));

    public OpenApiOperation tag(String tag) {
        this.tags.add(tag);
        return this;
    }

    public OpenApiOperation parameters(Collection<OpenApiPotentialReference<OpenApiParameter>> parameters) {
        this.parameters.addAll(parameters);
        return this;
    }

    public OpenApiOperation requestBody(Consumer<OpenApiRequestBody> requestBodyConsumer) {
        requestBody = new OpenApiRequestBody();
        requestBodyConsumer.accept(requestBody);
        return this;
    }

    public OpenApiOperation response(HttpStatusCode statusCode, Consumer<OpenApiResponse> responseConsumer) {
        var resp = new OpenApiResponse();
        responseConsumer.accept(resp);
        responses.put(statusCode, resp);
        return this;
    }

    public OpenApiOperation response(int statusCode, Consumer<OpenApiResponse> responseConsumer) {
        return response(HttpStatusCode.of(statusCode), responseConsumer);
    }

    public OpenApiResponse getResponse(int statusCode) {
        var resp = responses.get(HttpStatusCode.of(statusCode));
        if (resp == null) {
            return null;
        }
        return resp.getOriginalObject();
    }

    public OpenApiOperation eachResponse(BiConsumer<HttpStatusCode, OpenApiResponse> responseConsumer) {
        responses.forEach((code, resp) -> responseConsumer.accept(code, resp.getOriginalObject()));
        return this;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @EqualsAndHashCode
    public static class HttpStatusCode {
        @NonNull
        private final String code;

        public static final HttpStatusCode DEFAULT = new HttpStatusCode("default");

        public static HttpStatusCode of(int statusCode) {
            return new HttpStatusCode(Integer.toString(statusCode));
        }

        public Optional<Integer> getStatusCode() {
            if (this == DEFAULT) {
                return Optional.empty();
            }
            return Optional.of(Integer.parseInt(code));
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

        public boolean is(int statusCode) {
            try {
                return Integer.parseInt(code) == statusCode;
            }  catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        @JsonValue
        public String toString() {
            return code;
        }
    }
}
