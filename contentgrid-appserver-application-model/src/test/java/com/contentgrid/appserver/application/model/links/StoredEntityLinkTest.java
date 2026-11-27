package com.contentgrid.appserver.application.model.links;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.exceptions.InvalidEntityLinkException;
import com.contentgrid.appserver.application.model.links.LinkIdentity.UnnamedLink;
import com.contentgrid.appserver.application.model.links.UriTemplateDefinition.EntityLinkSubstitutionVariables;
import com.contentgrid.appserver.application.model.links.UriTemplateDefinition.SimpleUriTemplateDefinition;
import com.contentgrid.appserver.application.model.propertypath.SimpleAttributePath;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.hateoas.uritemplate.ParameterizedUriTemplateParser;
import java.net.URI;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

public class StoredEntityLinkTest {

    @Test
    void application_storedEntityLink_withPathsegmentWithTrailingSlash() {
        assertThrows(InvalidEntityLinkException.class, () -> StoredEntityLink.builder()
                .identity(new UnnamedLink(URI.create("https://example.com/rels/lookup")))
                .fallbackTemplate(new SimpleUriTemplateDefinition(
                        new ParameterizedUriTemplateParser<>(EnumSet.allOf(EntityLinkSubstitutionVariables.class))
                                .parseUnchecked("https://example.com/rels/lookup/%{application.id}")))
                .pathSegment(PathSegmentName.of("/_links/lookup/"))
                .storage(new SimpleAttributePath(AttributeName.of("lookup")))
                .build());
    }

}
