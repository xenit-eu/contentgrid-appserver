package com.contentgrid.appserver.application.model.i18n;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
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
    private final Function<Locale, M> newConstructor;

    @NonNull
    @Singular
    private final Map<String, BiFunction<M, String, M>> mappings;

    @NonNull
    private String bundleName;

    private final Map<Locale, T> translationCache = new ConcurrentHashMap<>();

    @With
    @Builder.Default
    private final String prefix = "";

    @With
    @Builder.Default
    private final List<String> suffixes = List.of("");

    public static <T, M extends T> ResourceBundleTranslatableBuilder<T, M> builder(Function<Locale, M> newConstructor) {
        return new ResourceBundleTranslatableBuilder<T, M>()
                .newConstructor(newConstructor);
    }

    public static <T, M extends T> ResourceBundleTranslatableBuilder<T, M> builder(Supplier<M> newConstructor) {
        return new ResourceBundleTranslatableBuilder<T, M>()
                .newConstructor(locale -> newConstructor.get());
    }

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
        return translationCache.computeIfAbsent(bundle.getLocale(), bundleLocale -> {
            var object = newConstructor.apply(bundleLocale);
            for (var entry : mappings.entrySet()) {
                for(var suffix: suffixes) {
                    try {
                        var translation = bundle.getString(prefix + entry.getKey() + suffix);
                        if (!translation.isEmpty()) {
                            object = entry.getValue().apply(object, translation);
                            break; // First matching non-empty suffix is used
                        }
                    } catch (MissingResourceException | NullPointerException | ClassCastException e) {
                        // mapping key did not exist, or mapping value was null or not a string
                        // Try mapping with next suffix
                    }
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
            return null;
        }
    }

    public static class ResourceBundleTranslatableBuilder<T, M extends T> {
        private ResourceBundleTranslatableBuilder<T, M> newConstructor(Function<Locale, M> newConstructor) {
            this.newConstructor = newConstructor;
            return this;
        }
    }

}
