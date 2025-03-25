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

    private static final String SOURCE_DESCRIPTION = "A link to the target of the source entity";
    private static final String TARGET_DESCRIPTION = "A link to the source of the target entity";

    @Test
    void oneToOne() {
        var oneToOneRelation = OneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .targetReference(ColumnName.of("target"))
                .build();
        assertEquals(SOURCE, oneToOneRelation.getSource().getEntity());
        assertEquals(TARGET, oneToOneRelation.getTarget().getEntity());
        assertEquals(RelationName.of("target"), oneToOneRelation.getSource().getName());
        assertNull(oneToOneRelation.getTarget().getName());
        assertEquals(ColumnName.of("target"), oneToOneRelation.getTargetReference());
        assertEquals(SOURCE_DESCRIPTION, oneToOneRelation.getSource().getDescription());
    }

    @Test
    void oneToOne_bidirectional() {
        var oneToOneRelation = OneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).description(TARGET_DESCRIPTION).build())
                .build();
        assertEquals(SOURCE, oneToOneRelation.getSource().getEntity());
        assertEquals(TARGET, oneToOneRelation.getTarget().getEntity());
        assertEquals(RelationName.of("target"), oneToOneRelation.getSource().getName());
        assertEquals(RelationName.of("source"), oneToOneRelation.getTarget().getName());
        assertEquals(ColumnName.of("target"), oneToOneRelation.getTargetReference());
        assertEquals(SOURCE_DESCRIPTION, oneToOneRelation.getSource().getDescription());
        assertEquals(TARGET_DESCRIPTION, oneToOneRelation.getTarget().getDescription());
    }

    @Test
    void oneToOne_missingSourceName() {
        var builder = OneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).description(TARGET_DESCRIPTION).build())
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
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .targetReference(ColumnName.of("target"))
                .build();
        assertEquals(SOURCE, manyToOneRelation.getSource().getEntity());
        assertEquals(TARGET, manyToOneRelation.getTarget().getEntity());
        assertEquals(RelationName.of("target"), manyToOneRelation.getSource().getName());
        assertNull(manyToOneRelation.getTarget().getName());
        assertEquals(ColumnName.of("target"), manyToOneRelation.getTargetReference());
        assertEquals(SOURCE_DESCRIPTION, manyToOneRelation.getSource().getDescription());
    }

    @Test
    void manyToOne_bidirectional() {
        var manyToOneRelation = ManyToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("sources")).description(TARGET_DESCRIPTION).build())
                .build();
        assertEquals(SOURCE, manyToOneRelation.getSource().getEntity());
        assertEquals(TARGET, manyToOneRelation.getTarget().getEntity());
        assertEquals(RelationName.of("target"), manyToOneRelation.getSource().getName());
        assertEquals(RelationName.of("sources"), manyToOneRelation.getTarget().getName());
        assertEquals(ColumnName.of("target"), manyToOneRelation.getTargetReference());
        assertEquals(SOURCE_DESCRIPTION, manyToOneRelation.getSource().getDescription());
        assertEquals(TARGET_DESCRIPTION, manyToOneRelation.getTarget().getDescription());
    }

    @Test
    void oneToMany() {
        var oneToManyRelation = OneToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .sourceReference(ColumnName.of("_source_id__targets"))
                .build();
        assertEquals(SOURCE, oneToManyRelation.getSource().getEntity());
        assertEquals(TARGET, oneToManyRelation.getTarget().getEntity());
        assertEquals(RelationName.of("targets"), oneToManyRelation.getSource().getName());
        assertNull(oneToManyRelation.getTarget().getName());
        assertEquals(ColumnName.of("_source_id__targets"), oneToManyRelation.getSourceReference());
        assertEquals(SOURCE_DESCRIPTION, oneToManyRelation.getSource().getDescription());
    }

    @Test
    void oneToMany_bidirectional() {
        var oneToManyRelation = OneToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).description(TARGET_DESCRIPTION).build())
                .build();
        assertEquals(SOURCE, oneToManyRelation.getSource().getEntity());
        assertEquals(TARGET, oneToManyRelation.getTarget().getEntity());
        assertEquals(RelationName.of("targets"), oneToManyRelation.getSource().getName());
        assertEquals(RelationName.of("source"), oneToManyRelation.getTarget().getName());
        assertEquals(ColumnName.of("source"), oneToManyRelation.getSourceReference());
        assertEquals(SOURCE_DESCRIPTION, oneToManyRelation.getSource().getDescription());
        assertEquals(TARGET_DESCRIPTION, oneToManyRelation.getTarget().getDescription());
    }

    @Test
    void manyToMany() {
        var manyToManyRelation = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).description(SOURCE_DESCRIPTION).build())
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
        assertEquals(SOURCE_DESCRIPTION, manyToManyRelation.getSource().getDescription());
    }

    @Test
    void manyToMany_bidirectional() {
        var manyToManyRelation = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("sources")).description(TARGET_DESCRIPTION).build())
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
        assertEquals(SOURCE_DESCRIPTION, manyToManyRelation.getSource().getDescription());
        assertEquals(TARGET_DESCRIPTION, manyToManyRelation.getTarget().getDescription());
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
        assertNull(manyToManyRelation.getSource().getDescription());
        assertNull(manyToManyRelation.getTarget().getDescription());
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