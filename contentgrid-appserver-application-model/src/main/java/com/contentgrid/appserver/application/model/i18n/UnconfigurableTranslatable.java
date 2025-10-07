package com.contentgrid.appserver.application.model.i18n;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@RequiredArgsConstructor
public class UnconfigurableTranslatable<T, M extends T> implements ConfigurableTranslatable<T, M> {

    @Delegate
    private final Translatable<T> delegate;

    @Override
    public ConfigurableTranslatable<T, M> withTranslations(Locale locale, M data) {
        return this;
    }

    @Override
    public ConfigurableTranslatable<T, M> withTranslationsBy(Locale locale, UnaryOperator<M> modifier) {
        return this;
    }

    @Override
    public ConfigurableTranslatable<T, M> withTranslationsBy(BiFunction<Locale, M, M> modifier) {
        return this;
    }
}
