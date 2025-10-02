package com.contentgrid.appserver.application.model.i18n;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Translatable where translations can be modified and new translations can be added
 * @param <T> The type holding the translations
 * @param <M> Extension of {@link T} that allows modification of translations
 */
public interface ConfigurableTranslatable<T, M extends T> extends Translatable<T> {

    /**
     * Adds or replaces translation data for a locale
     * @param locale The locale to add or replace the translations for
     * @param data The new translation data
     * @return A new instance with the translations replaced
     */
    ConfigurableTranslatable<T, M> withTranslations(Locale locale, M data);

    /**
     * Adds or updates translation data for a locale
     * @param locale The locale to add or modify the translations for
     * @param modifier Function that receives existing translation data as parameter and returns new translation data
     * @return A new instance with the translations updated
     */
    ConfigurableTranslatable<T, M> withTranslationsBy(Locale locale, UnaryOperator<M> modifier);

    /**
     * Updates existing translation data for all locales
     * @param modifier Function that receives locale and existing translation as parameter and returns new translation data
     * @return A new instance with the translations updated
     */
    ConfigurableTranslatable<T, M> withTranslationsBy(BiFunction<Locale, M, M> modifier);
}
