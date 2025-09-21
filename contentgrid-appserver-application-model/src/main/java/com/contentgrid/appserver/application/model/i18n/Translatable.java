package com.contentgrid.appserver.application.model.i18n;

import java.util.Locale;
import java.util.Map;

public interface Translatable<T> {

    Map<Locale, T> getTranslations();

    T getTranslations(UserLocales userLocales);

    T getTranslations(Locale locale);
}
