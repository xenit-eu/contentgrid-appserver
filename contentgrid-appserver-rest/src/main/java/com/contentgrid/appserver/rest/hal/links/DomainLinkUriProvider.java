package com.contentgrid.appserver.rest.hal.links;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.LinkUriProvider;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class DomainLinkUriProvider implements LinkUriProvider {
    private final LinkFactoryProvider linkFactoryProvider;

    @Override
    public String createEntityLink(EntityIdentity entityIdentity) {
        return linkFactoryProvider.toItem(entityIdentity).toUri().toASCIIString();
    }

    @Override
    public String createAttributeLink(EntityIdentity entityIdentity, AttributeName attributeName) {
        return linkFactoryProvider.toContent(entityIdentity, attributeName).toUri().toASCIIString();
    }

    @Override
    public String createRelationLink(EntityIdentity entityIdentity, RelationName relationName) {
        return linkFactoryProvider.toRelation(RelationIdentity.forRelation(entityIdentity, relationName))
                .orElseThrow()
                .toUri().toASCIIString();
    }
}
