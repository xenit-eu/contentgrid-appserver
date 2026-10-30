package com.contentgrid.appserver.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.links.LinkIdentity.NamedLink;
import com.contentgrid.appserver.application.model.links.LinkIdentity.UnnamedLink;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TextLinkVersionTest {

    private static final EntityIdentity ENTITY = EntityIdentity.forEntity(
            EntityName.of("person"), EntityId.of(UUID.fromString("01a18c91-0d81-717b-aedc-598a1a372849")));
    private static final NamedLink NAMED_LINK = new NamedLink(URI.create("https://example.com/rel"), "name");

    @Test
    void sameInputs_sameVersion() {
        assertThat(TextLinkVersion.calculate(ENTITY, NAMED_LINK, "value"))
                .isEqualTo(TextLinkVersion.calculate(ENTITY, NAMED_LINK, "value"));
    }

    @Test
    void differentValue_differentVersion() {
        assertThat(TextLinkVersion.calculate(ENTITY, NAMED_LINK, "value"))
                .isNotEqualTo(TextLinkVersion.calculate(ENTITY, NAMED_LINK, "other value"));
    }

    @Test
    void differentEntityName_differentVersion() {
        var otherEntity = EntityIdentity.forEntity(EntityName.of("invoice"), ENTITY.getEntityId());

        assertThat(TextLinkVersion.calculate(ENTITY, NAMED_LINK, "value"))
                .isNotEqualTo(TextLinkVersion.calculate(otherEntity, NAMED_LINK, "value"));
    }

    @Test
    void differentEntityId_differentVersion() {
        var otherEntity = EntityIdentity.forEntity(ENTITY.getEntityName(), EntityId.of(UUID.randomUUID()));

        assertThat(TextLinkVersion.calculate(ENTITY, NAMED_LINK, "value"))
                .isNotEqualTo(TextLinkVersion.calculate(otherEntity, NAMED_LINK, "value"));
    }

    @Test
    void differentRel_differentVersion() {
        var otherLink = new NamedLink(URI.create("https://example.com/other-rel"), NAMED_LINK.name());

        assertThat(TextLinkVersion.calculate(ENTITY, NAMED_LINK, "value"))
                .isNotEqualTo(TextLinkVersion.calculate(ENTITY, otherLink, "value"));
    }

    @Test
    void differentName_differentVersion() {
        var otherLink = new NamedLink(NAMED_LINK.rel(), "other-name");

        assertThat(TextLinkVersion.calculate(ENTITY, NAMED_LINK, "value"))
                .isNotEqualTo(TextLinkVersion.calculate(ENTITY, otherLink, "value"));
    }

    @Test
    void unnamedLink_usesEmptyName() {
        var unnamedLink = new UnnamedLink(NAMED_LINK.rel());
        var emptyNamedLink = new NamedLink(NAMED_LINK.rel(), "");

        assertThat(TextLinkVersion.calculate(ENTITY, unnamedLink, "value"))
                .isEqualTo(TextLinkVersion.calculate(ENTITY, emptyNamedLink, "value"))
                .isNotEqualTo(TextLinkVersion.calculate(ENTITY, NAMED_LINK, "value"));
    }
}