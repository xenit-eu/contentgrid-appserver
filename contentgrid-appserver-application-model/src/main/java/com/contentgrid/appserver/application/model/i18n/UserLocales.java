package com.contentgrid.appserver.application.model.i18n;

import java.util.Collection;
import java.util.Locale;

public interface UserLocales {
    static UserLocales defaults() {
        return new DefaultUserLocales();
    }
    Locale resolvePreferredLocale(Collection<Locale> supportedLocales);
}
