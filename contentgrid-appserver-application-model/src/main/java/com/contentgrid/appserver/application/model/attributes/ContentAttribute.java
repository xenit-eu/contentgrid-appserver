package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.IgnoredFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.i18n.ConfigurableTranslatable;
import com.contentgrid.appserver.application.model.i18n.Translatable;
import com.contentgrid.appserver.application.model.i18n.TranslatableImpl;
import com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.Delegate;

@Value
public class ContentAttribute implements CompositeAttribute {

    @NonNull
    AttributeName name;

    @EqualsAndHashCode.Exclude
    @NonNull
    @Delegate
    @Getter(value = AccessLevel.NONE)
    Translatable<AttributeTranslations> translations;

    @NonNull
    PathSegmentName pathSegment;

    @NonNull
    LinkName linkName;

    Set<AttributeFlag> flags;

    @NonNull
    SimpleAttribute id;

    @NonNull
    SimpleAttribute filename;

    @NonNull
    SimpleAttribute mimetype;

    @NonNull
    SimpleAttribute length;

    @Builder
    ContentAttribute(
            @NonNull AttributeName name,
            @NonNull ConfigurableTranslatable<AttributeTranslations, ConfigurableAttributeTranslations> translations,
            @Singular Set<AttributeFlag> flags,
            @NonNull PathSegmentName pathSegment,
            @NonNull LinkName linkName,
            @NonNull ColumnName idColumn,
            @NonNull ColumnName filenameColumn,
            @NonNull ColumnName mimetypeColumn,
            @NonNull ColumnName lengthColumn
    ) {
        this.name = name;
        this.translations = translations.withTranslationsBy(Locale.ROOT, t -> {
            if(t.getName() == null) {
                t = t.withName(name.getValue());
            }
            return t;
        });
        this.flags = flags;
        this.pathSegment = pathSegment;
        this.linkName = linkName;
        this.id = SimpleAttribute.builder().name(AttributeName.of("id")).column(idColumn)
                .type(Type.TEXT)
                .flag(IgnoredFlag.INSTANCE)
                .build();
        this.filename = SimpleAttribute.builder().name(AttributeName.of("filename")).column(filenameColumn)
                .type(Type.TEXT).build();
        this.mimetype = SimpleAttribute.builder().name(AttributeName.of("mimetype")).column(mimetypeColumn)
                .type(Type.TEXT).build();
        this.length = SimpleAttribute.builder().name(AttributeName.of("length")).column(lengthColumn)
                .type(Type.LONG)
                .flag(ReadOnlyFlag.INSTANCE)
                .build();

        for (var flag : this.flags) {
            flag.checkSupported(this);
        }
    }

    @Override
    public List<Attribute> getAttributes() {
        return Stream.of(id, filename, mimetype, length).collect(Collectors.toUnmodifiableList());
    }

    public static ContentAttributeBuilder builder() {
        return new ContentAttributeBuilder()
                .translations(new TranslatableImpl<>(ConfigurableAttributeTranslations::new));

    }

    public static class ContentAttributeBuilder extends TranslationBuilderSupport<AttributeTranslations, ConfigurableAttributeTranslations, ContentAttributeBuilder> {
        {
            getTranslations = () -> translations;
        }

        public ContentAttributeBuilder description(String description) {
            return translationsBy(Locale.ROOT, t -> t.withDescription(description));
        }

    }
}
