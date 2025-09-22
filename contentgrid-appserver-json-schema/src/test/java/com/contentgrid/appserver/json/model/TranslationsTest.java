package com.contentgrid.appserver.json.model;

import static org.junit.jupiter.api.Assertions.*;

import com.contentgrid.appserver.json.model.Translations.EmptyTranslation;
import com.contentgrid.appserver.json.model.Translations.MultipleTranslations;
import com.contentgrid.appserver.json.model.Translations.SingleTranslation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TranslationsTest {

    static Stream<Arguments> translations() {
        return Stream.of(
                Arguments.argumentSet("empty", "null", EmptyTranslation.INSTANCE),
                Arguments.argumentSet("single", "\"my-translation\"", new SingleTranslation("my-translation")),
                Arguments.argumentSet("multiple", """
                        {
                        "": "root-translation",
                        "fr": "french-translation",
                        "en_US": "us-translation"
                        }
                        """, new MultipleTranslations(Map.of(Locale.ROOT, "root-translation", Locale.FRENCH, "french-translation", Locale.US, "us-translation")))
        );
    }

    @ParameterizedTest
    @MethodSource("translations")
    void serialize(String serialized, Translations object) throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        var expectedTree = objectMapper.readTree(serialized);
        var serializedTree = objectMapper.valueToTree(object);

        assertEquals(expectedTree, serializedTree);
    }

    @ParameterizedTest
    @MethodSource("translations")
    void deserialize(String serialized, Translations object) throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        var deserializedObject = objectMapper.readValue(serialized, Translations.class);

        if (object == EmptyTranslation.INSTANCE) {
            assertNull(deserializedObject);
        } else {
            assertEquals(object, deserializedObject);
        }
    }

}