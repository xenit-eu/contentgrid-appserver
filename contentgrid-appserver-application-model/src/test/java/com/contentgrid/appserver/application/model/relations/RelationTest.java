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
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import org.junit.jupiter.api.Test;

class RelationTest {
    private static final SimpleAttribute ATTRIBUTE1 = SimpleAttribute.builder().name(AttributeName.of("attribute1")).type(Type.TEXT).column(
            ColumnName.of("column1")).build();
    private static final SimpleAttribute ATTRIBUTE2 = SimpleAttribute.builder().name(AttributeName.of("attribute2")).type(Type.TEXT).column(ColumnName.of("column2")).build();

    private static final Entity SOURCE = Entity.builder().name(EntityName.of("Source")).table(TableName.of("source")).pathSegment(PathSegmentName.of("sources")).attribute(ATTRIBUTE1).build();
    private static final Entity TARGET = Entity.builder().name(EntityName.of("Target")).table(TableName.of("target")).pathSegment(PathSegmentName.of("targets")).attribute(ATTRIBUTE2).build();

    private static final String SOURCE_DESCRIPTION = "A link to the target of the source entity";
    private static final String TARGET_DESCRIPTION = "A link to the source of the target entity";

    @Test
    void oneToOne() {
        var oneToOneRelation = SourceOneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).pathSegment(
                        PathSegmentName.of("target")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .targetReference(ColumnName.of("target"))
                .build();
        assertEquals(SOURCE, oneToOneRelation.getSource().getEntity());
        assertEquals(TARGET, oneToOneRelation.getTarget().getEntity());
        assertEquals(RelationName.of("target"), oneToOneRelation.getSource().getName());
        assertEquals(PathSegmentName.of("target"), oneToOneRelation.getSource().getPathSegment());
        assertNull(oneToOneRelation.getTarget().getName());
        assertNull(oneToOneRelation.getTarget().getPathSegment());
        assertEquals(ColumnName.of("target"), oneToOneRelation.getTargetReference());
        assertEquals(SOURCE_DESCRIPTION, oneToOneRelation.getSource().getDescription());
        assertFalse(oneToOneRelation.getSource().isRequired());
        assertFalse(oneToOneRelation.getTarget().isRequired());

        var inverseRelation = oneToOneRelation.inverse();
        assertInstanceOf(TargetOneToOneRelation.class, inverseRelation);
        assertEquals(oneToOneRelation.getSource(), inverseRelation.getTarget());
        assertEquals(oneToOneRelation.getTarget(), inverseRelation.getSource());
        assertEquals(oneToOneRelation, inverseRelation.inverse());
    }

    @Test
    void oneToOne_bidirectional() {
        var oneToOneRelation = SourceOneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).pathSegment(PathSegmentName.of("target")).description(SOURCE_DESCRIPTION).required(true).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).pathSegment(PathSegmentName.of("source")).description(TARGET_DESCRIPTION).build())
                .targetReference(ColumnName.of("target"))
                .build();
        assertEquals(SOURCE, oneToOneRelation.getSource().getEntity());
        assertEquals(TARGET, oneToOneRelation.getTarget().getEntity());
        assertEquals(RelationName.of("target"), oneToOneRelation.getSource().getName());
        assertEquals(PathSegmentName.of("target"), oneToOneRelation.getSource().getPathSegment());
        assertEquals(RelationName.of("source"), oneToOneRelation.getTarget().getName());
        assertEquals(PathSegmentName.of("source"), oneToOneRelation.getTarget().getPathSegment());
        assertEquals(ColumnName.of("target"), oneToOneRelation.getTargetReference());
        assertEquals(SOURCE_DESCRIPTION, oneToOneRelation.getSource().getDescription());
        assertEquals(TARGET_DESCRIPTION, oneToOneRelation.getTarget().getDescription());
        assertTrue(oneToOneRelation.getSource().isRequired());
        assertFalse(oneToOneRelation.getTarget().isRequired());

        var inverseRelation = oneToOneRelation.inverse();
        assertInstanceOf(TargetOneToOneRelation.class, inverseRelation);
        assertEquals(oneToOneRelation.getSource(), inverseRelation.getTarget());
        assertEquals(oneToOneRelation.getTarget(), inverseRelation.getSource());
        assertEquals(oneToOneRelation, inverseRelation.inverse());
    }

    @Test
    void oneToOne_missingSourceName() {
        var builder = SourceOneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).pathSegment(PathSegmentName.of("target")).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).pathSegment(PathSegmentName.of("source")).description(TARGET_DESCRIPTION).build())
                .targetReference(ColumnName.of("target"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void oneToOne_missingTargetName() {
        var builder = SourceOneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).pathSegment(PathSegmentName.of("target")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).pathSegment(PathSegmentName.of("source")).description(TARGET_DESCRIPTION).build())
                .targetReference(ColumnName.of("target"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void oneToOne_missingSourcePathSegment() {
        var builder = SourceOneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).pathSegment(PathSegmentName.of("source")).description(TARGET_DESCRIPTION).build())
                .targetReference(ColumnName.of("target"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void oneToOne_missingTargetPathSegment() {
        var builder = SourceOneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).pathSegment(PathSegmentName.of("target")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).description(TARGET_DESCRIPTION).build())
                .targetReference(ColumnName.of("target"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void oneToOne_reflexive_duplicateRelationName() {
        var builder = SourceOneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("source")).pathSegment(PathSegmentName.of("other")).build())
                .target(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("source")).pathSegment(PathSegmentName.of("source")).build())
                .targetReference(ColumnName.of("source"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void oneToOne_reflexive_duplicatePathSegment() {
        var builder = SourceOneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("other")).pathSegment(PathSegmentName.of("source")).build())
                .target(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("source")).pathSegment(PathSegmentName.of("source")).build())
                .targetReference(ColumnName.of("source"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void oneToOne_bothRequired() {
        var builder = SourceOneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).pathSegment(PathSegmentName.of("target")).description(SOURCE_DESCRIPTION).required(true).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).pathSegment(PathSegmentName.of("source")).description(TARGET_DESCRIPTION).required(true).build())
                .targetReference(ColumnName.of("target"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void oneToOne_requiredSourceMissingName() {
        var builder = SourceOneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).required(true).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).pathSegment(PathSegmentName.of("source")).description(TARGET_DESCRIPTION).build())
                .targetReference(ColumnName.of("target"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void oneToOne_requiredTargetMissingName() {
        var builder = SourceOneToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).pathSegment(PathSegmentName.of("target")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).required(true).build())
                .targetReference(ColumnName.of("target"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void manyToOne() {
        var manyToOneRelation = ManyToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).pathSegment(PathSegmentName.of("target")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .targetReference(ColumnName.of("target"))
                .build();
        assertEquals(SOURCE, manyToOneRelation.getSource().getEntity());
        assertEquals(TARGET, manyToOneRelation.getTarget().getEntity());
        assertEquals(RelationName.of("target"), manyToOneRelation.getSource().getName());
        assertEquals(PathSegmentName.of("target"), manyToOneRelation.getSource().getPathSegment());
        assertNull(manyToOneRelation.getTarget().getName());
        assertNull(manyToOneRelation.getTarget().getPathSegment());
        assertEquals(ColumnName.of("target"), manyToOneRelation.getTargetReference());
        assertEquals(SOURCE_DESCRIPTION, manyToOneRelation.getSource().getDescription());
        assertFalse(manyToOneRelation.getSource().isRequired());
        assertFalse(manyToOneRelation.getTarget().isRequired());

        var inverseRelation = manyToOneRelation.inverse();
        assertInstanceOf(OneToManyRelation.class, inverseRelation);
        assertEquals(manyToOneRelation.getSource(), inverseRelation.getTarget());
        assertEquals(manyToOneRelation.getTarget(), inverseRelation.getSource());
        assertEquals(manyToOneRelation, inverseRelation.inverse());
    }

    @Test
    void manyToOne_bidirectional() {
        var manyToOneRelation = ManyToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).pathSegment(PathSegmentName.of("target")).description(SOURCE_DESCRIPTION).required(true).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("sources")).pathSegment(PathSegmentName.of("sources")).description(TARGET_DESCRIPTION).build())
                .targetReference(ColumnName.of("target"))
                .build();
        assertEquals(SOURCE, manyToOneRelation.getSource().getEntity());
        assertEquals(TARGET, manyToOneRelation.getTarget().getEntity());
        assertEquals(RelationName.of("target"), manyToOneRelation.getSource().getName());
        assertEquals(PathSegmentName.of("target"), manyToOneRelation.getSource().getPathSegment());
        assertEquals(RelationName.of("sources"), manyToOneRelation.getTarget().getName());
        assertEquals(PathSegmentName.of("sources"), manyToOneRelation.getTarget().getPathSegment());
        assertEquals(ColumnName.of("target"), manyToOneRelation.getTargetReference());
        assertEquals(SOURCE_DESCRIPTION, manyToOneRelation.getSource().getDescription());
        assertEquals(TARGET_DESCRIPTION, manyToOneRelation.getTarget().getDescription());
        assertTrue(manyToOneRelation.getSource().isRequired());
        assertFalse(manyToOneRelation.getTarget().isRequired());

        var inverseRelation = manyToOneRelation.inverse();
        assertInstanceOf(OneToManyRelation.class, inverseRelation);
        assertEquals(manyToOneRelation.getSource(), inverseRelation.getTarget());
        assertEquals(manyToOneRelation.getTarget(), inverseRelation.getSource());
        assertEquals(manyToOneRelation, inverseRelation.inverse());
    }

    @Test
    void manyToOne_requiredTarget() {
        var builder = ManyToOneRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("target")).pathSegment(PathSegmentName.of("target")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("sources")).pathSegment(PathSegmentName.of("sources")).description(TARGET_DESCRIPTION).required(true).build())
                .targetReference(ColumnName.of("target"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void oneToMany() {
        var oneToManyRelation = OneToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).pathSegment(PathSegmentName.of("targets")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .sourceReference(ColumnName.of("_source_id__targets"))
                .build();
        assertEquals(SOURCE, oneToManyRelation.getSource().getEntity());
        assertEquals(TARGET, oneToManyRelation.getTarget().getEntity());
        assertEquals(RelationName.of("targets"), oneToManyRelation.getSource().getName());
        assertEquals(PathSegmentName.of("targets"), oneToManyRelation.getSource().getPathSegment());
        assertNull(oneToManyRelation.getTarget().getName());
        assertNull(oneToManyRelation.getTarget().getPathSegment());
        assertEquals(ColumnName.of("_source_id__targets"), oneToManyRelation.getSourceReference());
        assertEquals(SOURCE_DESCRIPTION, oneToManyRelation.getSource().getDescription());
        assertFalse(oneToManyRelation.getSource().isRequired());
        assertFalse(oneToManyRelation.getTarget().isRequired());

        var inverseRelation = oneToManyRelation.inverse();
        assertInstanceOf(ManyToOneRelation.class, inverseRelation);
        assertEquals(oneToManyRelation.getSource(), inverseRelation.getTarget());
        assertEquals(oneToManyRelation.getTarget(), inverseRelation.getSource());
        assertEquals(oneToManyRelation, inverseRelation.inverse());
    }

    @Test
    void oneToMany_bidirectional() {
        var oneToManyRelation = OneToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).pathSegment(PathSegmentName.of("targets")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).pathSegment(PathSegmentName.of("source")).description(TARGET_DESCRIPTION).required(true).build())
                .sourceReference(ColumnName.of("_source_id__targets"))
                .build();
        assertEquals(SOURCE, oneToManyRelation.getSource().getEntity());
        assertEquals(TARGET, oneToManyRelation.getTarget().getEntity());
        assertEquals(RelationName.of("targets"), oneToManyRelation.getSource().getName());
        assertEquals(PathSegmentName.of("targets"), oneToManyRelation.getSource().getPathSegment());
        assertEquals(RelationName.of("source"), oneToManyRelation.getTarget().getName());
        assertEquals(PathSegmentName.of("source"), oneToManyRelation.getTarget().getPathSegment());
        assertEquals(ColumnName.of("_source_id__targets"), oneToManyRelation.getSourceReference());
        assertEquals(SOURCE_DESCRIPTION, oneToManyRelation.getSource().getDescription());
        assertEquals(TARGET_DESCRIPTION, oneToManyRelation.getTarget().getDescription());
        assertFalse(oneToManyRelation.getSource().isRequired());
        assertTrue(oneToManyRelation.getTarget().isRequired());

        var inverseRelation = oneToManyRelation.inverse();
        assertInstanceOf(ManyToOneRelation.class, inverseRelation);
        assertEquals(oneToManyRelation.getSource(), inverseRelation.getTarget());
        assertEquals(oneToManyRelation.getTarget(), inverseRelation.getSource());
        assertEquals(oneToManyRelation, inverseRelation.inverse());
    }

    @Test
    void oneToMany_requiredSource() {
        var builder = OneToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).pathSegment(PathSegmentName.of("targets")).description(SOURCE_DESCRIPTION).required(true).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("source")).pathSegment(PathSegmentName.of("source")).description(TARGET_DESCRIPTION).build())
                .sourceReference(ColumnName.of("_source_id__targets"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void manyToMany() {
        var manyToManyRelation = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).pathSegment(PathSegmentName.of("targets")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).build())
                .joinTable(TableName.of("source__targets"))
                .sourceReference(ColumnName.of("source_id"))
                .targetReference(ColumnName.of("target_id"))
                .build();
        assertEquals(SOURCE, manyToManyRelation.getSource().getEntity());
        assertEquals(TARGET, manyToManyRelation.getTarget().getEntity());
        assertEquals(RelationName.of("targets"), manyToManyRelation.getSource().getName());
        assertEquals(PathSegmentName.of("targets"), manyToManyRelation.getSource().getPathSegment());
        assertNull(manyToManyRelation.getTarget().getName());
        assertNull(manyToManyRelation.getTarget().getPathSegment());
        assertEquals(TableName.of("source__targets"), manyToManyRelation.getJoinTable());
        assertEquals(ColumnName.of("source_id"), manyToManyRelation.getSourceReference());
        assertEquals(ColumnName.of("target_id"), manyToManyRelation.getTargetReference());
        assertEquals(SOURCE_DESCRIPTION, manyToManyRelation.getSource().getDescription());
        assertFalse(manyToManyRelation.getSource().isRequired());
        assertFalse(manyToManyRelation.getTarget().isRequired());

        var inverseRelation = manyToManyRelation.inverse();
        assertInstanceOf(ManyToManyRelation.class, inverseRelation);
        assertEquals(manyToManyRelation.getSource(), inverseRelation.getTarget());
        assertEquals(manyToManyRelation.getTarget(), inverseRelation.getSource());
        assertEquals(manyToManyRelation, inverseRelation.inverse());
    }

    @Test
    void manyToMany_bidirectional() {
        var manyToManyRelation = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).pathSegment(PathSegmentName.of("targets")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("sources")).pathSegment(PathSegmentName.of("sources")).description(TARGET_DESCRIPTION).build())
                .joinTable(TableName.of("source__targets"))
                .sourceReference(ColumnName.of("source_id"))
                .targetReference(ColumnName.of("target_id"))
                .build();
        assertEquals(SOURCE, manyToManyRelation.getSource().getEntity());
        assertEquals(TARGET, manyToManyRelation.getTarget().getEntity());
        assertEquals(RelationName.of("targets"), manyToManyRelation.getSource().getName());
        assertEquals(PathSegmentName.of("targets"), manyToManyRelation.getSource().getPathSegment());
        assertEquals(RelationName.of("sources"), manyToManyRelation.getTarget().getName());
        assertEquals(PathSegmentName.of("sources"), manyToManyRelation.getTarget().getPathSegment());
        assertEquals(TableName.of("source__targets"), manyToManyRelation.getJoinTable());
        assertEquals(ColumnName.of("source_id"), manyToManyRelation.getSourceReference());
        assertEquals(ColumnName.of("target_id"), manyToManyRelation.getTargetReference());
        assertEquals(SOURCE_DESCRIPTION, manyToManyRelation.getSource().getDescription());
        assertEquals(TARGET_DESCRIPTION, manyToManyRelation.getTarget().getDescription());
        assertFalse(manyToManyRelation.getSource().isRequired());
        assertFalse(manyToManyRelation.getTarget().isRequired());

        var inverseRelation = manyToManyRelation.inverse();
        assertInstanceOf(ManyToManyRelation.class, inverseRelation);
        assertEquals(manyToManyRelation.getSource(), inverseRelation.getTarget());
        assertEquals(manyToManyRelation.getTarget(), inverseRelation.getSource());
        assertEquals(manyToManyRelation, inverseRelation.inverse());
    }

    @Test
    void manyToMany_reflexive() {
        var manyToManyRelation = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("sources")).pathSegment(PathSegmentName.of("sources")).build())
                .target(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("others")).pathSegment(PathSegmentName.of("others")).build())
                .joinTable(TableName.of("source__sources"))
                .sourceReference(ColumnName.of("source_src_id"))
                .targetReference(ColumnName.of("source_tgt_id"))
                .build();
        assertEquals(SOURCE, manyToManyRelation.getSource().getEntity());
        assertEquals(SOURCE, manyToManyRelation.getTarget().getEntity());
        assertEquals(RelationName.of("sources"), manyToManyRelation.getSource().getName());
        assertEquals(PathSegmentName.of("sources"), manyToManyRelation.getSource().getPathSegment());
        assertEquals(RelationName.of("others"), manyToManyRelation.getTarget().getName());
        assertEquals(PathSegmentName.of("others"), manyToManyRelation.getTarget().getPathSegment());
        assertEquals(TableName.of("source__sources"), manyToManyRelation.getJoinTable());
        assertEquals(ColumnName.of("source_src_id"), manyToManyRelation.getSourceReference());
        assertEquals(ColumnName.of("source_tgt_id"), manyToManyRelation.getTargetReference());
        assertNull(manyToManyRelation.getSource().getDescription());
        assertNull(manyToManyRelation.getTarget().getDescription());
    }

    @Test
    void manyToMany_reflexive_duplicateRelationName() {
        var builder = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("sources")).pathSegment(PathSegmentName.of("sources")).build())
                .target(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("sources")).pathSegment(PathSegmentName.of("others")).build())
                .joinTable(TableName.of("source__sources"))
                .sourceReference(ColumnName.of("source_src_id"))
                .targetReference(ColumnName.of("source_tgt_id"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void manyToMany_reflexive_duplicateColumnReference() {
        var builder = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("sources")).pathSegment(PathSegmentName.of("sources")).build())
                .target(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("others")).pathSegment(PathSegmentName.of("others")).build())
                .joinTable(TableName.of("source__sources"))
                .sourceReference(ColumnName.of("source_id"))
                .targetReference(ColumnName.of("source_id"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void manyToMany_requiredSource() {
        var builder = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).pathSegment(PathSegmentName.of("targets")).description(SOURCE_DESCRIPTION).required(true).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("sources")).pathSegment(PathSegmentName.of("sources")).description(TARGET_DESCRIPTION).build())
                .joinTable(TableName.of("source__targets"))
                .sourceReference(ColumnName.of("source_id"))
                .targetReference(ColumnName.of("target_id"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

    @Test
    void manyToMany_requiredTarget() {
        var builder = ManyToManyRelation.builder()
                .source(RelationEndPoint.builder().entity(SOURCE).name(RelationName.of("targets")).pathSegment(PathSegmentName.of("targets")).description(SOURCE_DESCRIPTION).build())
                .target(RelationEndPoint.builder().entity(TARGET).name(RelationName.of("sources")).pathSegment(PathSegmentName.of("sources")).description(TARGET_DESCRIPTION).required(true).build())
                .joinTable(TableName.of("source__targets"))
                .sourceReference(ColumnName.of("source_id"))
                .targetReference(ColumnName.of("target_id"));
        assertThrows(InvalidRelationException.class, builder::build);
    }

}