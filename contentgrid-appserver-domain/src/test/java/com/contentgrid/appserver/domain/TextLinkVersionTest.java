package com.contentgrid.appserver.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.links.LinkIdentity.NamedLink;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import java.net.URI;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TextLinkVersionTest {

    private static final EntityIdentity ENTITY = EntityIdentity.forEntity(
            EntityName.of("person"), EntityId.of(UUID.fromString("01a18c91-0d81-717b-aedc-598a1a372849")));
    private static final NamedLink NAMED_LINK = new NamedLink(URI.create("https://example.com/rel"), "name");

    @Test
    void sameInputs_sameVersion() {
        assertThat(TextLinkVersion.calculate(ENTITY, NAMED_LINK, "value"))
                .isEqualTo(TextLinkVersion.calculate(ENTITY, NAMED_LINK, "value"));
    }

    @ParameterizedTest
    @MethodSource("provideInputsForDifferentVersion")
    void differentVersionPerCalculationComponent(EntityIdentity inputEntityIdentity, NamedLink inputLinkIdentity, String inputValue) {
        var baselineVersion = TextLinkVersion.calculate(ENTITY, NAMED_LINK, "value");
        var testVersion = TextLinkVersion.calculate(inputEntityIdentity, inputLinkIdentity, inputValue);
        assertThat(baselineVersion).isNotEqualTo(testVersion);
        assertThat(baselineVersion.isSatisfiedBy(testVersion)).isFalse();
    }

    private static Stream<Arguments> provideInputsForDifferentVersion() {
        return Stream.of(
                Arguments.of(ENTITY, NAMED_LINK, "other value"),
                Arguments.of(EntityIdentity.forEntity(EntityName.of("invoice"), ENTITY.getEntityId()), NAMED_LINK, "value"),
                Arguments.of(EntityIdentity.forEntity(ENTITY.getEntityName(), EntityId.of(UUID.randomUUID())), NAMED_LINK, "value"),
                Arguments.of(ENTITY, new NamedLink(URI.create("https://example.com/other-rel"), NAMED_LINK.name()), "value"),
                Arguments.of(ENTITY, new NamedLink(NAMED_LINK.rel(), "other-name"), "value")
        );
    }
}