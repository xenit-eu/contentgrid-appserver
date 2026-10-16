package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.domain.data.EntityLinkData;
import com.contentgrid.appserver.domain.data.EntityLinkInstance;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import lombok.Value;

@Value
class InternalEntityLinkInstance implements EntityLinkInstance {
    EntityIdentity entityIdentity;
    EntityLinkData entityLinkData;
}
