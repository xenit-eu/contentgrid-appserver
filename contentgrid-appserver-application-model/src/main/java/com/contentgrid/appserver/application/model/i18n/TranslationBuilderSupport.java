package com.contentgrid.appserver.application.model.i18n;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public abstract class TranslationBuilderSupport<T, B extends TranslationBuilderSupport<T, B>> {
    protected Supplier<ManipulatableTranslatable<T>> getTranslations;

    protected abstract B translations(ManipulatableTranslatable<T> translatable);

    public B translations(Locale locale, T data) {
        return translations(getTranslations.get().withTranslations(locale, data));
    }

    public B translationsBy(Locale locale, UnaryOperator<T> modifier) {
        return translations(getTranslations.get().withTranslationsBy(locale, modifier));
    }

    public B translationsBy(BiFunction<Locale, T, T> modifier) {
        return translations(getTranslations.get().withTranslationsBy(modifier));
    }

}
