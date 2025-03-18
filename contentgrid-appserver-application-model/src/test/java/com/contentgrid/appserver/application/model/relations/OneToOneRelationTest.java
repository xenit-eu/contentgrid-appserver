package com.contentgrid.appserver.application.model.relations;

import static org.junit.jupiter.api.Assertions.*;

import com.contentgrid.appserver.application.model.Attribute;
import com.contentgrid.appserver.application.model.Attribute.Type;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import org.junit.jupiter.api.Test;

class OneToOneRelationTest {

    @Test
    void test() {
        var attribute1 = Attribute.builder().name("attribute1").type(Type.TEXT).column("column1").build();
        var attribute2 = Attribute.builder().name("attribute2").type(Type.TEXT).column("column2").build();

        var source = Entity.builder().name("Source").table("source").attribute(attribute1).build();
        var target = Entity.builder().name("Target").table("target").attribute(attribute2).build();


        var oneToOneRelation = OneToOneRelation.builder()
                .source(
                        RelationEndPoint.builder().name("target").entity(source).build())
                .target(
                        RelationEndPoint.builder().entity(target).build()
                ).build();
        assertNotNull(oneToOneRelation);
    }

}