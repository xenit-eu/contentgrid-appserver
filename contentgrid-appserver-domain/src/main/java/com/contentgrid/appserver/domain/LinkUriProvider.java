package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.values.EntityIdentity;

public interface LinkUriProvider {
    String createEntityLink(EntityIdentity entityIdentity);
    String createAttributeLink(EntityIdentity entityIdentity, AttributeName attributeName);
    String createRelationLink(EntityIdentity entityIdentity, RelationName relationName);

}
