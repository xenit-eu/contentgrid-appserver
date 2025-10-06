package com.contentgrid.appserver.application.model.i18n;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.With;

@RequiredArgsConstructor
@Builder
public class ResourceBundleTranslatable<T, M extends T> implements Translatable<T> {
    @NonNull
    private final Supplier<M> newConstructor;

    @NonNull
    @Singular
    private final Map<String, BiFunction<M, String, M>> mappings;

    @NonNull
    private String bundleName;

    private final Map<Locale, T> translationCache = new ConcurrentHashMap<>();

    @With
    @Builder.Default
    private final String prefix = "";

    public ConfigurableTranslatable<T, M> asConfigurable() {
        return new UnconfigurableTranslatable<>(this);
    }

    @Override
    public Map<Locale, T> getTranslations() {
        return Map.of();
    }

    @Override
    public T getTranslations(UserLocales userLocales) {
        var supportedLocales = userLocales.preferredLocales()
                .map(this::loadBundle)
                .map(ResourceBundle::getLocale)
                .toList();

        var preferredLocale = userLocales.resolvePreferredLocale(supportedLocales);
        if(preferredLocale == null) {
            preferredLocale = Locale.ROOT;
        }

        return getTranslations(preferredLocale);
    }

    @Override
    public T getTranslations(Locale locale) {
        var bundle = loadBundle(locale);
        return translationCache.computeIfAbsent(bundle.getLocale(), unused -> {
            var object = newConstructor.get();
            for (var entry : mappings.entrySet()) {
                var translation = bundle.getString(prefix + entry.getKey());
                if(!translation.isEmpty()) {
                    object = entry.getValue().apply(object, translation);
                }
            }
            return object;
        });
    }

    private ResourceBundle loadBundle(Locale locale) {
        return ResourceBundle.getBundle(bundleName, locale, new CustomControl());
    }

    private static class CustomControl extends ResourceBundle.Control {

        @Override
        public List<String> getFormats(String baseName) {
            return FORMAT_PROPERTIES;
        }

        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            if(locale == Locale.ROOT) {
                return null;
            }

            return Locale.ROOT;
        }
    }
}
