package com.contentgrid.appserver.application.model.attributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;

class ContentAttributeTest {

    private static final UserLocales SUPPORTED_USERLOCALES = new UserLocales() {
        @Override
        public Locale resolvePreferredLocale(Collection<Locale> supportedLocales) {
            if(supportedLocales.contains(Locale.US)) {
                return Locale.US;
            }

            if(supportedLocales.contains(Locale.ENGLISH)) {
                return Locale.ENGLISH;
            }

            return null;
        }

        @Override
        public Stream<Locale> preferredLocales() {
            return Stream.of(Locale.US, Locale.ENGLISH);
        }
    };

    private static final UserLocales UNSUPPORTED_USERLOCALES = new UserLocales() {
        @Override
        public Locale resolvePreferredLocale(Collection<Locale> supportedLocales) {
            return null;
        }

        @Override
        public Stream<Locale> preferredLocales() {
            return Stream.of(Locale.CHINA, Locale.CHINESE);
        }
    };


    @Test
    void translations() {
        var attr = ContentAttribute.builder()
                .name(AttributeName.of("content"))
                .pathSegment(PathSegmentName.of("content"))
                .linkName(LinkName.of("content"))
                .idColumn(ColumnName.of("content__id"))
                .filenameColumn(ColumnName.of("content__filename"))
                .mimetypeColumn(ColumnName.of("content__mimetype"))
                .lengthColumn(ColumnName.of("content__length"))
                .build();

        assertThat(attr.getFilename().getTranslations(SUPPORTED_USERLOCALES).getName()).isEqualTo("Filename");
        assertThat(attr.getFilename().getTranslations(SUPPORTED_USERLOCALES).getDescription()).isNull();
        assertThat(attr.getLength().getTranslations(SUPPORTED_USERLOCALES).getName()).isEqualTo("Size");
        assertThat(attr.getLength().getTranslations(SUPPORTED_USERLOCALES).getDescription()).isEqualTo("File size in bytes");

        assertThat(attr.getFilename().getTranslations(UNSUPPORTED_USERLOCALES).getName()).isEqualTo("filename");
        assertThat(attr.getLength().getTranslations(UNSUPPORTED_USERLOCALES).getName()).isEqualTo("length");
    }

    @Nested
    class MimetypeConstraintTest {

        public static Stream<String> readLines(String file) throws IOException {
            var resourceAsStream = Objects.requireNonNull(MimetypeConstraintTest.class.getResourceAsStream(file));
            try(var reader = new BufferedReader(new InputStreamReader(
                    resourceAsStream))) {
                return reader.lines()
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .toList() // Need to materialize the stream before the try-with-resources closes the inputstream
                        .stream();
            }
        }

        public static Stream<String> validMimetypes() throws IOException {
            return readLines("valid-mimetypes.txt");
        }

        public static Stream<String> invalidMimetypes() throws IOException {
            return readLines("invalid-mimetypes.txt");
        }

        @ParameterizedTest
        @MethodSource
        void validMimetypes(String mediaType) {
            // server-side validation pattern matches
            assertThat(ContentAttribute.MIMETYPE_PATTERN_CONSTRAINT.getPattern().matcher(mediaType).matches()).isTrue();
            // client-side validation pattern matches
            assertThat(Pattern.matches(ContentAttribute.MIMETYPE_PATTERN_CONSTRAINT.getHtmlPattern(), mediaType)).isTrue();
            // media type parsing is also valid
            assertThatCode(() -> {
                MediaType.parseMediaType(mediaType);
            }).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @MethodSource
        void invalidMimetypes(String mediaType) {
            assertThat(ContentAttribute.MIMETYPE_PATTERN_CONSTRAINT.getPattern().matcher(mediaType).matches()).isFalse();
        }

    }

}