package com.contentgrid.appserver.application.model.i18n;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TranslatableImpl<T, M extends T> implements ConfigurableTranslatable<T, M> {
    @NonNull
    private final Function<Locale, M> newConstructor;

    @NonNull
    private final Map<Locale, M> translations;

    public TranslatableImpl(@NonNull Supplier<M> newConstructor, @NonNull Map<Locale, M> translations) {
        this(locale -> newConstructor.get(), translations);
    }

    public TranslatableImpl(@NonNull Supplier<M> newConstructor) {
        this(newConstructor, Map.of());
    }

    public TranslatableImpl(@NonNull Function<Locale, M> newConstructor) {
        this(newConstructor, Map.of());
    }

    @Override
    public Map<Locale, T> getTranslations() {
        return Collections.unmodifiableMap(translations);
    }

    @Override
    public T getTranslations(@NonNull UserLocales userLocales) {
        var preferredLocale = userLocales.resolvePreferredLocale(translations.keySet());
        if(preferredLocale == null) {
            preferredLocale = Locale.ROOT;
        }
        return getTranslations(preferredLocale);
    }

    public T getTranslations(@NonNull Locale locale) {
        return getOrNewTranslation(locale);
    }

    private M getOrNewTranslation(@NonNull Locale locale) {
        var translation = translations.get(locale);
        if (translation != null) {
            return translation;
        }
        return newConstructor.apply(locale);
    }

    @Override
    public ConfigurableTranslatable<T, M> withTranslations(@NonNull Locale locale, @NonNull M data) {
        var copy = new HashMap<>(translations);
        copy.put(locale, data);
        return new TranslatableImpl<>(
                newConstructor,
                copy
        );
    }

    @Override
    public ConfigurableTranslatable<T, M> withTranslationsBy(@NonNull Locale locale, @NonNull UnaryOperator<M> modifier) {
        var existing = getOrNewTranslation(locale);
        return withTranslations(locale, modifier.apply(existing));
    }

    @Override
    public ConfigurableTranslatable<T, M> withTranslationsBy(BiFunction<Locale, M, M> modifier) {
         var newTranslations = translations.entrySet().stream()
                 .collect(Collectors.toUnmodifiableMap(
                         Entry::getKey,
                         e -> modifier.apply(e.getKey(), e.getValue())
                 ));

         return new TranslatableImpl<>(
                 newConstructor,
                 newTranslations
         );
    }
}
