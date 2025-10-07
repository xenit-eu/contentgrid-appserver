package com.contentgrid.appserver.application.model.i18n;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.stream.Stream;

class DefaultUserLocales implements UserLocales {
    private static final List<LanguageRange> defaultLocales = LanguageRange.parse("en");

    @Override
    public Locale resolvePreferredLocale(Collection<Locale> supportedLocales) {
        var exactMatch = Locale.lookup(defaultLocales, supportedLocales);
        if(exactMatch != null) {
            return exactMatch;
        }
        var filtered = Locale.filter(defaultLocales, supportedLocales);
        if(!filtered.isEmpty()) {
            return filtered.getFirst();
        }
        return null;
    }

    @Override
    public Stream<Locale> preferredLocales() {
        return Stream.of(Locale.ENGLISH);
    }
}
