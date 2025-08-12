package com.contentgrid.appserver.application.model.relations.flags;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;

public interface RelationEndpointFlag {

    void checkSupported(Relation relation, RelationEndPoint endPoint);
}
