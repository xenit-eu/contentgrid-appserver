package com.contentgrid.appserver.json;

import com.contentgrid.appserver.application.model.i18n.TranslationBuilderSupport;
import com.contentgrid.appserver.json.model.Translations;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Singular;

@RequiredArgsConstructor
@Builder
class TranslationConverter<T, U> {
    @NonNull
    @Singular
    private final Map<Function<U, Translations>, BiFunction<T, String, T>> mappers;

    public <B extends TranslationBuilderSupport<? super T, T, B>> B mapInto(U source, B builder) {
        for (var mapperEntry : mappers.entrySet()) {
            var translations = mapperEntry.getKey().apply(source);

            if(translations == null) {
                continue;
            }

            for (var localeStringEntry : translations.getTranslations().entrySet()) {
                builder = builder.translationsBy(
                        localeStringEntry.getKey(),
                        t -> mapperEntry.getValue().apply(t,localeStringEntry.getValue())
                );
            }
        }

        return builder;
    }


}
