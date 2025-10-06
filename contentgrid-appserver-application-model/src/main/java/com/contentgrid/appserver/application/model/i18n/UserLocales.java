package com.contentgrid.appserver.application.model.i18n;

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Strategy-interface that resolves the available locales into the most suitable one for a user
 */
public interface UserLocales {
    static UserLocales defaults() {
        return new DefaultUserLocales();
    }

    /**
     * Resolve the preferred locale from a collection of available locales
     * @param supportedLocales Locales that are available for picking a preferred one from
     * @return The preferred locale if one is available, or {@code null} when none of the supported locales is suitable at all
     */
    Locale resolvePreferredLocale(Collection<Locale> supportedLocales);

    /**
     * Retrieve an ordered stream of preferred locales
     * @return A stream of locales preferred by the user in order of preference
     */
    Stream<Locale> preferredLocales();
}
