package com.contentgrid.appserver.application.model.relations;

import static org.junit.jupiter.api.Assertions.*;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.exceptions.InvalidRelationException;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import org.junit.jupiter.api.Test;

class RelationTest {
    private static final SimpleAttribute ATTRIBUTE1 = SimpleAttribute.builder().name(AttributeName.of("attribute1")).type(Type.TEXT).column(
            ColumnName.of("column1")).build();
    private static final SimpleAttribute ATTRIBUTE2 = SimpleAttribute.builder().name(AttributeName.of("attribute2")).type(Type.TEXT).column(ColumnName.of("column2")).build();

    private static final Entity SOURCE = Entity.builder().name(EntityName.of("Source")).table(TableName.of("source")).attribute(ATTRIBUTE1).build();
    private static final Entity TARGET = Entity.builder().name(EntityName.of("Target")).table(TableName.of("target")).attribute(ATTRIBUTE2).build();

    @Test
    void oneToOne() {
        var oneToOneRelation = OneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .targetReference(ColumnName.of("target"))
                .build();
        assertEquals(SOURCE, oneToOneRelation.getSource().getEntity());
        assertEquals(TARGET, oneToOneRelation.getTarget().getEntity());
        assertEquals(RelationName.of("target"), oneToOneRelation.getSource().getName());
        assertNull(oneToOneRelation.getTarget().getName());
        assertEquals(ColumnName.of("target"), oneToOneRelation.getTargetReference());
    }

    @Test
    void oneToOne_bidirectional() {
        var oneToOneRelation = OneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).build())
                .targetReference(ColumnName.of("target"))
                .build();
        assertEquals(SOURCE, oneToOneRelation.getSource().getEntity());
        assertEquals(TARGET, oneToOneRelation.getTarget().getEntity());
        assertEquals(RelationName.of("target"), oneToOneRelation.getSource().getName());
        assertEquals(RelationName.of("source"), oneToOneRelation.getTarget().getName());
        assertEquals(ColumnName.of("target"), oneToOneRelation.getTargetReference());
    }

    @Test
    void oneToOne_missingSourceName() {
        var builder = OneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).build())
                .targetReference(ColumnName.of("target"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void oneToOne_reflexive_duplicateRelationName() {
        var builder = OneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("source")).build())
                .target(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("source")).build())
                .targetReference(ColumnName.of("source"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void manyToOne() {
        var manyToOneRelation = ManyToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .targetReference(ColumnName.of("target"))
                .build();
        assertEquals(SOURCE, manyToOneRelation.getSource().getEntity());
        assertEquals(TARGET, manyToOneRelation.getTarget().getEntity());
        assertEquals(RelationName.of("target"), manyToOneRelation.getSource().getName());
        assertNull(manyToOneRelation.getTarget().getName());
        assertEquals(ColumnName.of("target"), manyToOneRelation.getTargetReference());
    }

    @Test
    void manyToOne_bidirectional() {
        var manyToOneRelation = ManyToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("sources")).build())
                .targetReference(ColumnName.of("target"))
                .build();
        assertEquals(SOURCE, manyToOneRelation.getSource().getEntity());
        assertEquals(TARGET, manyToOneRelation.getTarget().getEntity());
        assertEquals(RelationName.of("target"), manyToOneRelation.getSource().getName());
        assertEquals(RelationName.of("sources"), manyToOneRelation.getTarget().getName());
        assertEquals(ColumnName.of("target"), manyToOneRelation.getTargetReference());
    }

    @Test
    void oneToMany() {
        var oneToManyRelation = OneToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .sourceReference(ColumnName.of("_source_id__targets"))
                .build();
        assertEquals(SOURCE, oneToManyRelation.getSource().getEntity());
        assertEquals(TARGET, oneToManyRelation.getTarget().getEntity());
        assertEquals(RelationName.of("targets"), oneToManyRelation.getSource().getName());
        assertNull(oneToManyRelation.getTarget().getName());
        assertEquals(ColumnName.of("_source_id__targets"), oneToManyRelation.getSourceReference());
    }

    @Test
    void oneToMany_bidirectional() {
        var oneToManyRelation = OneToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).build())
                .sourceReference(ColumnName.of("_source_id__targets"))
                .build();
        assertEquals(SOURCE, oneToManyRelation.getSource().getEntity());
        assertEquals(TARGET, oneToManyRelation.getTarget().getEntity());
        assertEquals(RelationName.of("targets"), oneToManyRelation.getSource().getName());
        assertEquals(RelationName.of("source"), oneToManyRelation.getTarget().getName());
        assertEquals(ColumnName.of("_source_id__targets"), oneToManyRelation.getSourceReference());
    }

    @Test
    void manyToMany() {
        var manyToManyRelation = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .joinTable(TableName.of("source__targets"))
                .sourceReference(ColumnName.of("source_id"))
                .targetReference(ColumnName.of("target_id"))
                .build();
        assertEquals(SOURCE, manyToManyRelation.getSource().getEntity());
        assertEquals(TARGET, manyToManyRelation.getTarget().getEntity());
        assertEquals(RelationName.of("targets"), manyToManyRelation.getSource().getName());
        assertNull(manyToManyRelation.getTarget().getName());
        assertEquals(TableName.of("source__targets"), manyToManyRelation.getJoinTable());
        assertEquals(ColumnName.of("source_id"), manyToManyRelation.getSourceReference());
        assertEquals(ColumnName.of("target_id"), manyToManyRelation.getTargetReference());
    }

    @Test
    void manyToMany_bidirectional() {
        var manyToManyRelation = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("sources")).build())
                .joinTable(TableName.of("source__targets"))
                .sourceReference(ColumnName.of("source_id"))
                .targetReference(ColumnName.of("target_id"))
                .build();
        assertEquals(SOURCE, manyToManyRelation.getSource().getEntity());
        assertEquals(TARGET, manyToManyRelation.getTarget().getEntity());
        assertEquals(RelationName.of("targets"), manyToManyRelation.getSource().getName());
        assertEquals(RelationName.of("sources"), manyToManyRelation.getTarget().getName());
        assertEquals(TableName.of("source__targets"), manyToManyRelation.getJoinTable());
        assertEquals(ColumnName.of("source_id"), manyToManyRelation.getSourceReference());
        assertEquals(ColumnName.of("target_id"), manyToManyRelation.getTargetReference());
    }

    @Test
    void manyToMany_reflexive() {
        var manyToManyRelation = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("sources")).build())
                .target(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("others")).build())
                .joinTable(TableName.of("source__sources"))
                .sourceReference(ColumnName.of("source_src_id"))
                .targetReference(ColumnName.of("source_tgt_id"))
                .build();
        assertEquals(SOURCE, manyToManyRelation.getSource().getEntity());
        assertEquals(SOURCE, manyToManyRelation.getTarget().getEntity());
        assertEquals(RelationName.of("sources"), manyToManyRelation.getSource().getName());
        assertEquals(RelationName.of("others"), manyToManyRelation.getTarget().getName());
        assertEquals(TableName.of("source__sources"), manyToManyRelation.getJoinTable());
        assertEquals(ColumnName.of("source_src_id"), manyToManyRelation.getSourceReference());
        assertEquals(ColumnName.of("source_tgt_id"), manyToManyRelation.getTargetReference());
    }

    @Test
    void manyToMany_reflexive_duplicateRelationName() {
        var builder = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("sources")).build())
                .target(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("sources")).build())
                .joinTable(TableName.of("source__sources"))
                .sourceReference(ColumnName.of("source_src_id"))
                .targetReference(ColumnName.of("source_tgt_id"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void manyToMany_reflexive_duplicateColumnReference() {
        var builder = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("sources")).build())
                .target(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("others")).build())
                .joinTable(TableName.of("source__sources"))
                .sourceReference(ColumnName.of("source_id"))
                .targetReference(ColumnName.of("source_id"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

}