package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.links.LinkIdentity;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.values.EntityIdentity;

public interface LinkUriProvider { //TODO ACC-3004 change naming to `createXyzUri`
    String createEntityLink(EntityIdentity entityIdentity);
    String createAttributeLink(EntityIdentity entityIdentity, AttributeName attributeName);
    String createRelationLink(EntityIdentity entityIdentity, RelationName relationName);
    String createStoredDataLinkLink(EntityIdentity entityIdentity, LinkIdentity linkIdentity);

}
