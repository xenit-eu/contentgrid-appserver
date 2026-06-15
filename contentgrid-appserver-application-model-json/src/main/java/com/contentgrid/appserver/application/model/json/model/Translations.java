package com.contentgrid.appserver.application.model.json.model;

import com.contentgrid.appserver.application.model.json.model.Translations.TranslationsDeserializer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.Value;

@JsonDeserialize(using = TranslationsDeserializer.class)
public sealed interface Translations {
    Map<Locale, String> getTranslations();

    Translations omitIfEqualTo(String value);

    @Value
    class EmptyTranslation implements Translations {
        public static final EmptyTranslation INSTANCE = new EmptyTranslation();

        @Override
        public Map<Locale, String> getTranslations() {
            return Map.of();
        }

        @Override
        public Translations omitIfEqualTo(String value) {
            return this;
        }

        @JsonValue
        public String getValue() {
            return null;
        }
    }

    @JsonDeserialize(using = ValueDeserializer.None.class)
    @Value
    class SingleTranslation implements Translations {
        @JsonValue
        String value;

        @Override
        public Map<Locale, String> getTranslations() {
            return Map.of(Locale.ROOT, value);
        }

        @Override
        public Translations omitIfEqualTo(String value) {
            if(Objects.equals(this.value, value)) {
                return EmptyTranslation.INSTANCE;
            }
            return this;
        }
    }

    @JsonDeserialize(using = ValueDeserializer.None.class)
    @Value
    class MultipleTranslations implements Translations {

        @JsonValue
        Map<Locale, String> translations;

        @JsonCreator
        public static MultipleTranslations from(Map<Locale, String> map) {
            return new MultipleTranslations(map);
        }

        @Override
        public Translations omitIfEqualTo(String value) {
            if(Objects.equals(translations.get(Locale.ROOT), value)) {
                var copy = new LinkedHashMap<>(translations);
                copy.remove(Locale.ROOT);
                return new MultipleTranslations(copy);
            }
            return this;
        }
    }

    class TranslationsDeserializer extends ValueDeserializer<Translations> {
        @Override
        public Translations deserialize(JsonParser p, DeserializationContext ctxt) {
            var t = p.currentToken();
            if (t == JsonToken.VALUE_NULL) {
                return EmptyTranslation.INSTANCE;
            }
            if (t == JsonToken.VALUE_STRING) {
                return ctxt.readValue(p, SingleTranslation.class);
            }
            if (t == JsonToken.START_OBJECT) {
                return ctxt.readValue(p, MultipleTranslations.class);
            }
            return ctxt.reportInputMismatch(Translations.class, "Expected string or object");
        }
    }


}
