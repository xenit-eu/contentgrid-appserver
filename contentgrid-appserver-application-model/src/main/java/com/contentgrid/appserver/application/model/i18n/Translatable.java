package com.contentgrid.appserver.application.model.i18n;

import java.util.Locale;
import java.util.Map;

/**
 * Provides translations for a component
 * @param <T> The type holding the translations
 */
public interface Translatable<T> {

    /**
     * Retrieve the full mapping of all translations
     * <p>
     * This should only be used for extracting all translations at once,
     * use {@link #getTranslations(UserLocales)} or {@link #getTranslations(Locale)} for obtaining specific translations
     * for a locale
     * @return A full mapping of all translations by locale
     */
    Map<Locale, T> getTranslations();

    /**
     * Retrieve the most suitable translation for a user's locales
     * @param userLocales The user's locales
     * @return The most suitable translation for the user. Will never be null, but may be an empty object when there are no fallback translations
     */
    T getTranslations(UserLocales userLocales);

    /**
     * Retrieve the translation for a specific locale
     * @param locale The locale to retrieve translations for
     * @return The translations for this locale. Will never be null, but may be an empty object when the locale does not have translations
     */
    T getTranslations(Locale locale);
}
