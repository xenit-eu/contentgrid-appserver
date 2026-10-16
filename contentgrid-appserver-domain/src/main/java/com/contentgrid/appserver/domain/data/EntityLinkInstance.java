package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.domain.values.EntityIdentity;

public interface EntityLinkInstance {

    EntityIdentity getEntityIdentity();
    EntityLinkData getEntityLinkData();

}
