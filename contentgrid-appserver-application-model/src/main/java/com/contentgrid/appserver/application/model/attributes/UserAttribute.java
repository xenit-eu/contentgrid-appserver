package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.IgnoredFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.exceptions.MissingFlagException;
import com.contentgrid.appserver.application.model.i18n.ManipulatableTranslatable;
import com.contentgrid.appserver.application.model.i18n.Translatable;
import com.contentgrid.appserver.application.model.i18n.TranslatableImpl;
import com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
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
public class UserAttribute implements CompositeAttribute {

    @NonNull
    AttributeName name;

    @EqualsAndHashCode.Exclude
    @NonNull
    @Delegate
    @Getter(value = AccessLevel.NONE)
    Translatable<AttributeTranslations> translations;

    Set<AttributeFlag> flags;

    @NonNull
    SimpleAttribute id;

    @NonNull
    SimpleAttribute namespace;

    @NonNull
    SimpleAttribute username;

    @Builder
    UserAttribute(@NonNull AttributeName name, ManipulatableTranslatable<AttributeTranslations> translations, @Singular Set<AttributeFlag> flags,
            @NonNull ColumnName idColumn, @NonNull ColumnName namespaceColumn, @NonNull ColumnName usernameColumn) {
        this.name = name;
        this.translations = translations.withTranslationsBy(Locale.ROOT, t -> {
            if(t.getName() == null) {
                t = t.withName(name.getValue());
            }
            return t;
        });
        if (flags.stream().anyMatch(flag -> flag instanceof ReadOnlyFlag || flag instanceof IgnoredFlag)) {
            this.flags = flags;
        } else {
            throw new MissingFlagException("Writing to user attributes not supported, a ReadOnlyFlag or IgnoredFlag is required");
        }
        this.id = SimpleAttribute.builder().name(AttributeName.of("id")).column(idColumn)
                .type(Type.TEXT)
                .flag(IgnoredFlag.INSTANCE)
                .build();
        this.namespace = SimpleAttribute.builder().name(AttributeName.of("namespace")).column(namespaceColumn)
                .type(Type.TEXT)
                .flag(IgnoredFlag.INSTANCE)
                .build();
        this.username = SimpleAttribute.builder().name(AttributeName.of("name")).column(usernameColumn)
                .type(Type.TEXT).build();

        for (var flag : this.flags) {
            flag.checkSupported(this);
        }
    }

    @Override
    public List<Attribute> getAttributes() {
        return Stream.of(id, namespace, username).collect(Collectors.toUnmodifiableList());
    }

    public static UserAttributeBuilder builder() {
        return new UserAttributeBuilder()
                .translations(new TranslatableImpl<>(AttributeTranslations::new));
    }

    public static class UserAttributeBuilder extends TranslationBuilderSupport<AttributeTranslations, UserAttributeBuilder> {
        {
            getTranslations = () -> translations;
        }

        public UserAttributeBuilder description(String description) {
            return translationsBy(Locale.ROOT, t -> t.withDescription(description));
        }

    }
}
