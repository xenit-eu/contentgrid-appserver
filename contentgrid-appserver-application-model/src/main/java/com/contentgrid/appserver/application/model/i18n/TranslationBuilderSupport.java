package com.contentgrid.appserver.application.model.i18n;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public abstract class TranslationBuilderSupport<T, M extends T, B extends TranslationBuilderSupport<T, M, B>> {
    protected Supplier<ConfigurableTranslatable<T, M>> getTranslations;

    protected abstract B translations(ConfigurableTranslatable<T, M> translatable);

    public B translations(Locale locale, M data) {
        return translations(getTranslations.get().withTranslations(locale, data));
    }

    public B translationsBy(Locale locale, UnaryOperator<M> modifier) {
        return translations(getTranslations.get().withTranslationsBy(locale, modifier));
    }

    public B translationsBy(BiFunction<Locale, M, M> modifier) {
        return translations(getTranslations.get().withTranslationsBy(modifier));
    }

}
