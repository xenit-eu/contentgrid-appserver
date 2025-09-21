package com.contentgrid.appserver.application.model.i18n;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public interface ManipulatableTranslatable<T> extends Translatable<T> {

    ManipulatableTranslatable<T> withTranslations(Locale locale, T data);

    ManipulatableTranslatable<T> withTranslationsBy(Locale locale, UnaryOperator<T> modifier);

    ManipulatableTranslatable<T> withTranslationsBy(BiFunction<Locale, T, T> modifier);
}
