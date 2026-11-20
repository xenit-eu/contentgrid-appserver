package com.contentgrid.appserver.application.model.links;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.links.LinkIdentity.NamedLink;
import java.net.URI;
import org.junit.jupiter.api.Test;

public class LinkIdentityTest {

    // Positive tests
    @Test
    void namedLinkTest() {
        var namedLink = new NamedLink(URI.create("https://example.com/rels/lookup"), "actualValidLinkName");
        assertThat(namedLink)
                .hasFieldOrPropertyWithValue("rel", URI.create("https://example.com/rels/lookup"))
                .hasFieldOrPropertyWithValue("name", "actualValidLinkName");
    }

    // Negative tests
    @Test
    void namedLink_emptyName_throws() {
        assertThrows(IllegalArgumentException.class, () -> new NamedLink(URI.create("https://example.com/rels/lookup/"), ""));
    }

    @Test
    void namedLink_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () -> new NamedLink(URI.create("https://example.com/rels/lookup/"), "   "));
    }

    @Test
    void namdLink_nameWithTrailingSlash_throws() {
        assertThrows(IllegalArgumentException.class, () -> new NamedLink(URI.create("https://example.com/rels/lookup/"), "actualInvalidLinkName/"));
    }



}
