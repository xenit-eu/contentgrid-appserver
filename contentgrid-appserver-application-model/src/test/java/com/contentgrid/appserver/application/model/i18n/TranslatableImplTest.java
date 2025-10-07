package com.contentgrid.appserver.application.model.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.With;
import org.junit.jupiter.api.Test;

class TranslatableImplTest {

    public static final Locale DUTCH = Locale.of("nl");

    interface TestTranslations {
        String getTitle();
        String getDescription();
    }

    @Value
    @With
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static class ConfigurableTestTranslations implements TestTranslations {
        String title;
        String description;
    }

    private static final ConfigurableTranslatable<TestTranslations, ConfigurableTestTranslations> EMPTY = new TranslatableImpl<>(
            ConfigurableTestTranslations::new);

    private static final ConfigurableTranslatable<TestTranslations, ConfigurableTestTranslations> STANDARD = EMPTY
                .withTranslationsBy(Locale.ENGLISH, t  -> t.withTitle("My title").withDescription("My description"))
                .withTranslationsBy(Locale.FRENCH, t -> t.withTitle("Mon titre").withDescription("Mon description"))
            ;

    @Test
    void manipulate_translation() {
        var withTranslations = STANDARD
                .withTranslationsBy(Locale.ENGLISH, t -> t.withTitle("New title"))
                .withTranslationsBy(DUTCH, t -> t.withTitle("Mijn titel"));

        assertThat(withTranslations.getTranslations())
                .containsOnlyKeys(Locale.ENGLISH, Locale.FRENCH, DUTCH);

        assertThat(withTranslations.getTranslations(Locale.ENGLISH))
                .extracting(TestTranslations::getTitle)
                .isEqualTo("New title");
        assertThat(withTranslations.getTranslations(Locale.ENGLISH))
                .extracting(TestTranslations::getDescription)
                .isEqualTo("My description");
    }

    @Test
    void set_translation() {
        var withTranslations = STANDARD
                .withTranslations(Locale.ENGLISH, new ConfigurableTestTranslations("New title", null));

        assertThat(withTranslations.getTranslations())
                .containsOnlyKeys(Locale.ENGLISH, Locale.FRENCH);

        assertThat(withTranslations.getTranslations(Locale.ENGLISH))
                .extracting(TestTranslations::getTitle)
                .isEqualTo("New title");
        assertThat(withTranslations.getTranslations(Locale.ENGLISH))
                .extracting(TestTranslations::getDescription)
                .isNull();
    }

    @Test
    void modify_all_translations() {
        var withTranslations = STANDARD
                .withTranslationsBy((locale, t) -> t.withDescription(t.getTitle()));

        assertThat(withTranslations.getTranslations())
                .containsOnlyKeys(Locale.ENGLISH, Locale.FRENCH);

        assertThat(withTranslations.getTranslations(Locale.ENGLISH))
                .extracting(TestTranslations::getTitle)
                .isEqualTo("My title");
        assertThat(withTranslations.getTranslations(Locale.ENGLISH))
                .extracting(TestTranslations::getDescription)
                .isEqualTo("My title");

        assertThat(withTranslations.getTranslations(Locale.FRENCH))
                .extracting(TestTranslations::getDescription)
                .isEqualTo("Mon titre");
    }

    @Test
    void get_translations() {
        var fakeUserLocale = new UserLocales() {
            @Override
            public Locale resolvePreferredLocale(Collection<Locale> supportedLocales) {
                if(supportedLocales.contains(DUTCH)) {
                    return DUTCH;
                }
                if(supportedLocales.contains(Locale.ENGLISH)) {
                    return Locale.ENGLISH;
                }
                return null;
            }

            @Override
            public Stream<Locale> preferredLocales() {
                return Stream.of(DUTCH, Locale.ENGLISH);
            }
        };
        var translations = STANDARD.getTranslations(fakeUserLocale);

        assertThat(translations.getTitle()).isEqualTo("My title");

        var withDutch = STANDARD.withTranslationsBy(DUTCH, t -> t.withTitle("Mijn titel"));

        assertThat(withDutch.getTranslations(fakeUserLocale).getTitle()).isEqualTo("Mijn titel");

        assertThat(EMPTY.getTranslations(fakeUserLocale)).isNotNull()
                .extracting(TestTranslations::getTitle)
                .isNull();
    }

}