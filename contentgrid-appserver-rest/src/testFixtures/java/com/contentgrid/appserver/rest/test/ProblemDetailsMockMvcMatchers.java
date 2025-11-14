package com.contentgrid.appserver.rest.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.With;
import org.assertj.core.api.ThrowingConsumer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProblemDetailsMockMvcMatchers {

    private final static ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
    }

    public static ProblemDetailsMatcher problemDetails() {
        return new ProblemDetailsMatcher();
    }

    public static ValidationConstraintViolationMatcher validationConstraintViolation() {
        return new ValidationConstraintViolationMatcher(List.of());
    }

    @With
    @AllArgsConstructor
    public static class ProblemDetailsMatcher implements ResultMatcher {

        private static final String SENTINEL = "\0";
        private final String type;
        private final String title;
        private final String detail;
        private final HttpStatusCode statusCode;

        @With(AccessLevel.PRIVATE)
        private final Map<String, ThrowingConsumer<Object>> fields;

        public ProblemDetailsMatcher() {
            this(SENTINEL, SENTINEL, SENTINEL, null, Map.of());
        }

        ProblemDetail readProblemDetail(MvcResult result) throws IOException {
            assertThat(result.getResponse().getContentType())
                    .as("response Content-Type")
                    .isEqualTo("application/problem+json");

            var problemDetails = objectMapper.reader()
                    .readValue(result.getResponse().getContentAsByteArray(), ProblemDetail.class);

            if (statusCode != null) {
                assertThat(result.getResponse().getStatus())
                        .as("response status code")
                        .isEqualTo(statusCode.value());
                assertThat(problemDetails)
                        .extracting(ProblemDetail::getStatus)
                        .as("problem status")
                        .isEqualTo(statusCode.value());
            }

            if (!Objects.equals(type, SENTINEL)) {
                assertThat(problemDetails)
                        .extracting(ProblemDetail::getType)
                        .extracting(URI::toString)
                        .as("problem type")
                        .isEqualTo(type);
            }
            if (!Objects.equals(title, SENTINEL)) {
                assertThat(problemDetails)
                        .extracting(ProblemDetail::getTitle)
                        .as("problem title")
                        .isEqualTo(title);
            }
            if (!Objects.equals(detail, SENTINEL)) {
                assertThat(problemDetails)
                        .extracting(ProblemDetail::getDetail)
                        .as("problem detail")
                        .isEqualTo(detail);
            }

            for (var field : fields.entrySet()) {
                assertThat(problemDetails.getProperties())
                        .extractingByKey(field.getKey())
                        .satisfies(field.getValue());
            }

            return problemDetails;
        }

        public ProblemDetailsMatcher withField(String field, Object fieldValue) {
            return withField(field, v -> assertThat(v).isEqualTo(fieldValue));
        }

        public ProblemDetailsMatcher withField(String field, ThrowingConsumer<?> fieldValue) {
            var copy = new LinkedHashMap<>(fields);
            copy.put(field, (ThrowingConsumer<Object>) fieldValue);
            return withFields(copy);
        }

        @Override
        public void match(MvcResult result) throws Exception {
            readProblemDetail(result);
        }
    }

    @AllArgsConstructor
    public static class ValidationConstraintViolationMatcher implements ResultMatcher {

        private final static ProblemDetailsMatcher PROBLEM_DETAILS_MATCHER = new ProblemDetailsMatcher()
                .withStatusCode(HttpStatus.BAD_REQUEST)
                .withType("https://contentgrid.cloud/problems/input/validation");

        @With(AccessLevel.PRIVATE)
        private final List<ErrorDescription> errors;

        public ValidationConstraintViolationMatcher withError(ErrorDescription description) {
            return withErrors(Stream.concat(
                    errors.stream(),
                    Stream.of(description)
            ).toList());
        }

        public ValidationConstraintViolationMatcher withError(UnaryOperator<ErrorDescription> configurer) {
            return withError(configurer.apply(new ErrorDescription()));
        }

        @Override
        public void match(MvcResult result) throws Exception {
            var details = PROBLEM_DETAILS_MATCHER.readProblemDetail(result);
            var properties = details.getProperties();
            assertThat(properties).containsKey("errors")
                    .extractingByKey("errors")
                    .isInstanceOf(List.class);

            var errors = (List) properties.get("errors");

            assertThat(errors)
                    .satisfiesExactlyInAnyOrder(
                            this.errors.stream().map(ErrorDescription::toSatisfies).toArray(ThrowingConsumer[]::new));

        }

        @RequiredArgsConstructor
        public static class ErrorDescription {

            private final Map<String, ThrowingConsumer<Object>> fields = new LinkedHashMap<>();

            public ErrorDescription withType(ThrowingConsumer<String> type) {
                return withField("type", type);
            }

            public ErrorDescription withTitle(ThrowingConsumer<String> title) {
                return withField("title", title);
            }

            public ErrorDescription withDetail(ThrowingConsumer<String> detail) {
                return withField("detail", detail);
            }

            public ErrorDescription withProperty(ThrowingConsumer<String> property) {
                return withField("property", property);
            }

            public ErrorDescription withType(String value) {
                return withType(t -> assertThat(t).isEqualTo(value));
            }

            public ErrorDescription withTitle(String value) {
                return withTitle(t -> assertThat(t).isEqualTo(value));
            }

            public ErrorDescription withDetail(String value) {
                return withDetail(t -> assertThat(t).isEqualTo(value));
            }

            public ErrorDescription withProperty(String value) {
                return withProperty(t -> assertThat(t).isEqualTo(value));
            }

            public ErrorDescription withField(String field, Object fieldValue) {
                return withField(field, t -> assertThat(t).isEqualTo(fieldValue));
            }

            public ErrorDescription withField(String field, ThrowingConsumer<?> fieldValue) {
                var copy =  new ErrorDescription();
                copy.fields.putAll(fields);
                copy.fields.put(field, (ThrowingConsumer<Object>) fieldValue);
                return copy;
            }

            ThrowingConsumer<Map<String, Object>> toSatisfies() {
                return (data) -> {
                    for (var field : fields.entrySet()) {
                        assertThat(data)
                                .extractingByKey(field.getKey())
                                .satisfies(field.getValue());
                    }
                };

            }
        }
    }
}
