package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import lombok.Value;

@Value
public class RelationTarget {
    RelationIdentity relationIdentity;
    EntityIdentity targetEntityIdentity;
}
