package com.contentgrid.appserver.json;

import com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport;
import com.contentgrid.appserver.json.model.Translations;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Singular;

/**
 * Map translations between JSON format and {@link TranslationBuilderSupport}-implementing builders
 * <p>
 * Extracts translations from the JSON data object and applies it to the builder.
 *
 * @param <T> the target type that holds translations for a particular language (e.g. {@link com.contentgrid.appserver.application.model.attributes.Attribute.AttributeTranslations})
 * @param <U> the source type containing translation data
 *
 * @see com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport
 * @see com.contentgrid.appserver.json.model.Translations
 */
@RequiredArgsConstructor
@Builder
class TranslationConverter<T, U> {

    /**
     * @param <U> JSON data object to extract translations from
     */
    interface TranslationExtractor<U> {
        Translations extract(U jsonData);
    }

    /**
     * @param <T> Object holding translations for a particular language
     */
    interface TranslationApplyer<T> {
        T applyTo(T translationObject, String translationString);
    }

    @NonNull
    @Singular
    private final Map<TranslationExtractor<U>, TranslationApplyer<T>> mappers;

    /**
     * Maps translations from the source object into the target builder.
     *
     * @param <B> the builder type that extends {@link TranslationBuilderSupport}
     * @param source the JSON data object containing translation data
     * @param builder the builder to apply translations to
     * @return the builder enhanced with all applicable translations
     */
    public <B extends TranslationBuilderSupport<? super T, T, B>> B mapInto(U source, B builder) {
        for (var mapperEntry : mappers.entrySet()) {
            var translations = mapperEntry.getKey().extract(source);

            if(translations == null) {
                continue;
            }

            for (var localeStringEntry : translations.getTranslations().entrySet()) {
                builder = builder.translationsBy(
                        localeStringEntry.getKey(),
                        t -> mapperEntry.getValue().applyTo(t, localeStringEntry.getValue())
                );
            }
        }

        return builder;
    }


}
