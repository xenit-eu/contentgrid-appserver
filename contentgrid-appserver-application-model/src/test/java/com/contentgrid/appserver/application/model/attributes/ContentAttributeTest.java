package com.contentgrid.appserver.application.model.attributes;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

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

}