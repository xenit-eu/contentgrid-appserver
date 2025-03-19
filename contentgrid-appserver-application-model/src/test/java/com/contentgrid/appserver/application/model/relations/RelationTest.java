package com.contentgrid.appserver.application.model.relations;

import static org.junit.jupiter.api.Assertions.*;

import com.contentgrid.appserver.application.model.Attribute;
import com.contentgrid.appserver.application.model.Attribute.Type;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import org.junit.jupiter.api.Test;

class RelationTest {
    private static final Attribute ATTRIBUTE1 = Attribute.builder().name("attribute1").type(Type.TEXT).column("column1").build();
    private static final Attribute ATTRIBUTE2 = Attribute.builder().name("attribute2").type(Type.TEXT).column("column2").build();

    private static final Entity SOURCE = Entity.builder().name("Source").table("source").attribute(ATTRIBUTE1).build();
    private static final Entity TARGET = Entity.builder().name("Target").table("target").attribute(ATTRIBUTE2).build();

    @Test
    void oneToOne() {
        var oneToOneRelation = OneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name("target").build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .targetReference("target")
                .build();
        assertEquals(SOURCE, oneToOneRelation.getSource().getEntity());
        assertEquals(TARGET, oneToOneRelation.getTarget().getEntity());
        assertEquals("target", oneToOneRelation.getSource().getName());
        assertNull(oneToOneRelation.getTarget().getName());
        assertEquals("target", oneToOneRelation.getTargetReference());
    }

    @Test
    void oneToOne_bidirectional() {
        var oneToOneRelation = OneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name("target").build())
                .target(RelationEndPoint.builder().entity(TARGET).name("source").build())
                .targetReference("target")
                .build();
        assertEquals(SOURCE, oneToOneRelation.getSource().getEntity());
        assertEquals(TARGET, oneToOneRelation.getTarget().getEntity());
        assertEquals("target", oneToOneRelation.getSource().getName());
        assertEquals("source", oneToOneRelation.getTarget().getName());
        assertEquals("target", oneToOneRelation.getTargetReference());
    }

    @Test
    void oneToOne_missingSourceName() {
        var builder = OneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).build())
                .target(RelationEndPoint.builder().entity(TARGET).name("source").build())
                .targetReference("target");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void oneToOne_reflexive_duplicateRelationName() {
        var builder = OneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name("source").build())
                .target(RelationEndPoint.builder().entity(SOURCE).name("source").build())
                .targetReference("source");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void manyToOne() {
        var manyToOneRelation = ManyToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name("target").build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .targetReference("target")
                .build();
        assertEquals(SOURCE, manyToOneRelation.getSource().getEntity());
        assertEquals(TARGET, manyToOneRelation.getTarget().getEntity());
        assertEquals("target", manyToOneRelation.getSource().getName());
        assertNull(manyToOneRelation.getTarget().getName());
        assertEquals("target", manyToOneRelation.getTargetReference());
    }

    @Test
    void manyToOne_bidirectional() {
        var manyToOneRelation = ManyToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name("target").build())
                .target(RelationEndPoint.builder().entity(TARGET).name("sources").build())
                .targetReference("target")
                .build();
        assertEquals(SOURCE, manyToOneRelation.getSource().getEntity());
        assertEquals(TARGET, manyToOneRelation.getTarget().getEntity());
        assertEquals("target", manyToOneRelation.getSource().getName());
        assertEquals("sources", manyToOneRelation.getTarget().getName());
        assertEquals("target", manyToOneRelation.getTargetReference());
    }

    @Test
    void oneToMany() {
        var oneToManyRelation = OneToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name("targets").build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .sourceReference("_source_id__targets")
                .build();
        assertEquals(SOURCE, oneToManyRelation.getSource().getEntity());
        assertEquals(TARGET, oneToManyRelation.getTarget().getEntity());
        assertEquals("targets", oneToManyRelation.getSource().getName());
        assertNull(oneToManyRelation.getTarget().getName());
        assertEquals("_source_id__targets", oneToManyRelation.getSourceReference());
    }

    @Test
    void oneToMany_bidirectional() {
        var oneToManyRelation = OneToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name("targets").build())
                .target(RelationEndPoint.builder().entity(TARGET).name("source").build())
                .sourceReference("_source_id__targets")
                .build();
        assertEquals(SOURCE, oneToManyRelation.getSource().getEntity());
        assertEquals(TARGET, oneToManyRelation.getTarget().getEntity());
        assertEquals("targets", oneToManyRelation.getSource().getName());
        assertEquals("source", oneToManyRelation.getTarget().getName());
        assertEquals("_source_id__targets", oneToManyRelation.getSourceReference());
    }

    @Test
    void manyToMany() {
        var manyToManyRelation = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name("targets").build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .joinTable("source__targets")
                .sourceReference("source_id")
                .targetReference("target_id")
                .build();
        assertEquals(SOURCE, manyToManyRelation.getSource().getEntity());
        assertEquals(TARGET, manyToManyRelation.getTarget().getEntity());
        assertEquals("targets", manyToManyRelation.getSource().getName());
        assertNull(manyToManyRelation.getTarget().getName());
        assertEquals("source__targets", manyToManyRelation.getJoinTable());
        assertEquals("source_id", manyToManyRelation.getSourceReference());
        assertEquals("target_id", manyToManyRelation.getTargetReference());
    }

    @Test
    void manyToMany_bidirectional() {
        var manyToManyRelation = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name("targets").build())
                .target(RelationEndPoint.builder().entity(TARGET).name("sources").build())
                .joinTable("source__targets")
                .sourceReference("source_id")
                .targetReference("target_id")
                .build();
        assertEquals(SOURCE, manyToManyRelation.getSource().getEntity());
        assertEquals(TARGET, manyToManyRelation.getTarget().getEntity());
        assertEquals("targets", manyToManyRelation.getSource().getName());
        assertEquals("sources", manyToManyRelation.getTarget().getName());
        assertEquals("source__targets", manyToManyRelation.getJoinTable());
        assertEquals("source_id", manyToManyRelation.getSourceReference());
        assertEquals("target_id", manyToManyRelation.getTargetReference());
    }

    @Test
    void manyToMany_reflexive() {
        var manyToManyRelation = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name("sources").build())
                .target(RelationEndPoint.builder().entity(SOURCE).name("others").build())
                .joinTable("source__sources")
                .sourceReference("source_src_id")
                .targetReference("source_tgt_id")
                .build();
        assertEquals(SOURCE, manyToManyRelation.getSource().getEntity());
        assertEquals(SOURCE, manyToManyRelation.getTarget().getEntity());
        assertEquals("sources", manyToManyRelation.getSource().getName());
        assertEquals("others", manyToManyRelation.getTarget().getName());
        assertEquals("source__sources", manyToManyRelation.getJoinTable());
        assertEquals("source_src_id", manyToManyRelation.getSourceReference());
        assertEquals("source_tgt_id", manyToManyRelation.getTargetReference());
    }

    @Test
    void manyToMany_reflexive_duplicateRelationName() {
        var builder = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name("sources").build())
                .target(RelationEndPoint.builder().entity(SOURCE).name("sources").build())
                .joinTable("source__sources")
                .sourceReference("source_src_id")
                .targetReference("source_tgt_id");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void manyToMany_reflexive_duplicateColumnReference() {
        var builder = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name("sources").build())
                .target(RelationEndPoint.builder().entity(SOURCE).name("others").build())
                .joinTable("source__sources")
                .sourceReference("source_id")
                .targetReference("source_id");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

}