package com.contentgrid.appserver.query.engine.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JoinCollectionTest {

    private static final Entity PERSON = Entity.builder()
            .name(EntityName.of("person"))
            .table(TableName.of("person"))
            .pathSegment(PathSegmentName.of("persons"))
            .linkName(LinkName.of("persons"))
            .build();

    private static final Entity INVOICE = Entity.builder()
            .name(EntityName.of("invoice"))
            .table(TableName.of("invoice"))
            .pathSegment(PathSegmentName.of("invoices"))
            .linkName(LinkName.of("invoices"))
            .build();

    private static final Entity PRODUCT = Entity.builder()
            .name(EntityName.of("product"))
            .table(TableName.of("product"))
            .pathSegment(PathSegmentName.of("products"))
            .linkName(LinkName.of("products"))
            .build();

    private static final OneToOneRelation INVOICE_PREVIOUS = SourceOneToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("previous_invoice"))
                    .pathSegment(PathSegmentName.of("previous-invoice"))
                    .linkName(LinkName.of("previous_invoice"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("next_invoice"))
                    .pathSegment(PathSegmentName.of("next-invoice"))
                    .linkName(LinkName.of("next_invoice"))
                    .build())
            .targetReference(ColumnName.of("previous_invoice"))
            .build();

    private static final OneToOneRelation INVOICE_NEXT = (OneToOneRelation) INVOICE_PREVIOUS.inverse();

    private static final ManyToOneRelation INVOICE_CUSTOMER = ManyToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("customer"))
                    .pathSegment(PathSegmentName.of("customer"))
                    .linkName(LinkName.of("customer"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(PERSON)
                    .name(RelationName.of("invoices"))
                    .pathSegment(PathSegmentName.of("invoices"))
                    .linkName(LinkName.of("invoices"))
                    .build())
            .targetReference(ColumnName.of("customer"))
            .build();

    private static final OneToManyRelation PERSON_INVOICES = (OneToManyRelation) INVOICE_CUSTOMER.inverse();

    private static final ManyToManyRelation INVOICE_PRODUCTS = ManyToManyRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("products"))
                    .pathSegment(PathSegmentName.of("products"))
                    .linkName(LinkName.of("products"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(PRODUCT)
                    .name(RelationName.of("invoices"))
                    .pathSegment(PathSegmentName.of("invoices"))
                    .linkName(LinkName.of("invoices"))
                    .build())
            .joinTable(TableName.of("invoice__products"))
            .sourceReference(ColumnName.of("invoice_id"))
            .targetReference(ColumnName.of("product_id"))
            .build();

    private static final ManyToManyRelation PRODUCT_INVOICES = (ManyToManyRelation) INVOICE_PRODUCTS.inverse();

    private static Stream<Arguments> relations() {
        return Stream.of(
                // no relation
                Arguments.of(INVOICE, List.of(), (UnaryOperator<Condition>) condition -> condition),
                // source one-to-one relation
                Arguments.of(INVOICE, List.of(INVOICE_PREVIOUS), (UnaryOperator<Condition>) condition ->
                        DSL.exists(DSL.selectOne()
                                .from(DSL.table("invoice").as("i1"))
                                .where(DSL.and(
                                        DSL.field(DSL.name("i1", "id"), UUID.class)
                                                .eq(DSL.field(DSL.name("i0", "previous_invoice"), UUID.class)),
                                        condition
                                )))),
                // target one-to-one relation
                Arguments.of(INVOICE, List.of(INVOICE_NEXT), (UnaryOperator<Condition>) condition ->
                        DSL.exists(DSL.selectOne()
                                .from(DSL.table("invoice").as("i1"))
                                .where(DSL.and(
                                        DSL.field(DSL.name("i1", "previous_invoice"), UUID.class)
                                                .eq(DSL.field(DSL.name("i0", "id"), UUID.class)),
                                        condition
                                )))),
                // many-to-one relation
                Arguments.of(INVOICE, List.of(INVOICE_CUSTOMER), (UnaryOperator<Condition>) condition ->
                        DSL.exists(DSL.selectOne()
                                .from(DSL.table("person").as("p1"))
                                .where(DSL.and(
                                        DSL.field(DSL.name("p1", "id"), UUID.class)
                                                .eq(DSL.field(DSL.name("i0", "customer"), UUID.class)),
                                        condition
                                )))),
                // one-to-many relation
                Arguments.of(PERSON, List.of(PERSON_INVOICES), (UnaryOperator<Condition>) condition ->
                        DSL.exists(DSL.selectOne()
                                .from(DSL.table("invoice").as("i1"))
                                .where(DSL.and(
                                        DSL.field(DSL.name("i1", "customer"), UUID.class)
                                                .eq(DSL.field(DSL.name("p0", "id"), UUID.class)),
                                        condition
                                )))),
                // many-to-many relation
                Arguments.of(INVOICE, List.of(INVOICE_PRODUCTS), (UnaryOperator<Condition>) condition ->
                        DSL.exists(DSL.selectOne()
                                .from(DSL.table("invoice__products").as("i1"))
                                .join(DSL.table("product").as("p2"))
                                .on(DSL.field(DSL.name("p2", "id"), UUID.class)
                                        .eq(DSL.field(DSL.name("i1", "product_id"), UUID.class)))
                                .where(DSL.and(
                                        DSL.field(DSL.name("i1", "invoice_id"), UUID.class)
                                                .eq(DSL.field(DSL.name("i0", "id"), UUID.class)),
                                        condition
                                )))),
                // many-to-many relation + many-to-one relation (3 entities)
                Arguments.of(PRODUCT, List.of(PRODUCT_INVOICES, INVOICE_CUSTOMER), (UnaryOperator<Condition>) condition ->
                        DSL.exists(DSL.selectOne()
                                .from(DSL.table("invoice__products").as("i1"))
                                .join(DSL.table("invoice").as("i2"))
                                .on(DSL.field(DSL.name("i2", "id"), UUID.class)
                                        .eq(DSL.field(DSL.name("i1", "invoice_id"), UUID.class)))
                                .join(DSL.table("person").as("p3"))
                                .on(DSL.field(DSL.name("p3", "id"), UUID.class)
                                        .eq(DSL.field(DSL.name("i2", "customer"), UUID.class)))
                                .where(DSL.and(
                                        DSL.field(DSL.name("i1", "product_id"), UUID.class)
                                                .eq(DSL.field(DSL.name("p0", "id"), UUID.class)),
                                        condition
                                ))))
        );
    }

    @ParameterizedTest
    @MethodSource("relations")
    void collectTest(Entity entity, List<Relation> relations, UnaryOperator<Condition> operator) {
        var joins = new JoinCollection(entity.getTable());
        var condition = DSL.condition(true);

        // collect() without addRelation() has no effect
        assertEquals(condition, joins.collect(condition));
        assertEquals(joins.getRootTable(), joins.getCurrentTable());
        assertEquals(joins.getRootAlias(), joins.getCurrentAlias());

        relations.forEach(joins::addRelation);
        // currentTable should be the target table of the last relation
        if (!relations.isEmpty()) {
            assertEquals(relations.getLast().getTargetEndPoint().getEntity().getTable(), joins.getCurrentTable());
        }
        var result = joins.collect(condition);

        // collect() should return expected result and reset current table
        var expected = operator.apply(condition);
        assertEquals(expected, result);
        assertEquals(joins.getRootTable(), joins.getCurrentTable());
        assertEquals(joins.getRootAlias(), joins.getCurrentAlias());

        // collect() after collect() has no effect
        assertEquals(condition, joins.collect(condition));
        assertEquals(joins.getRootTable(), joins.getCurrentTable());
        assertEquals(joins.getRootAlias(), joins.getCurrentAlias());
    }

    @ParameterizedTest
    @MethodSource("relations")
    void resetCurrentTableTest(Entity entity, List<Relation> relations, UnaryOperator<Condition> operator) {
        var joins = new JoinCollection(entity.getTable());
        var condition = DSL.condition(true);

        // after creation
        assertEquals(joins.getRootTable(), joins.getCurrentTable());
        assertEquals(joins.getRootAlias(), joins.getCurrentAlias());

        // resetCurrentTable() without addRelation() has no effect
        joins.resetCurrentTable();
        assertEquals(joins.getRootTable(), joins.getCurrentTable());
        assertEquals(joins.getRootAlias(), joins.getCurrentAlias());

        relations.forEach(joins::addRelation);
        // currentTable should be the target table of the last relation
        if (!relations.isEmpty()) {
            assertEquals(relations.getLast().getTargetEndPoint().getEntity().getTable(), joins.getCurrentTable());
        }

        // resetCurrentTable() should set currentTable back to rootTable
        joins.resetCurrentTable();
        assertEquals(joins.getRootTable(), joins.getCurrentTable());
        assertEquals(joins.getRootAlias(), joins.getCurrentAlias());

        // collect() after resetCurrentTable() should still give expected result
        var result = joins.collect(condition);
        var expected = operator.apply(condition);
        assertEquals(expected, result);
        assertEquals(joins.getRootTable(), joins.getCurrentTable());
        assertEquals(joins.getRootAlias(), joins.getCurrentAlias());

        // resetCurrentTable() after collect() has no effect
        joins.resetCurrentTable();
        assertEquals(joins.getRootTable(), joins.getCurrentTable());
        assertEquals(joins.getRootAlias(), joins.getCurrentAlias());
    }

    @Test
    void multipleTerms() {
        var joins = new JoinCollection(INVOICE.getTable());
        var condition = DSL.condition(true);

        joins.addRelation(INVOICE_CUSTOMER); // left term
        joins.resetCurrentTable();
        joins.addRelation(INVOICE_PRODUCTS); // right term

        var expected = DSL.exists(DSL.selectOne()
                .from(DSL.table("person").as("p1"))
                .join(DSL.table("invoice__products").as("i2"))
                .on(DSL.field(DSL.name("i2", "invoice_id"), UUID.class)
                        .eq(DSL.field(DSL.name("i0", "id"), UUID.class)))
                .join(DSL.table("product").as("p3"))
                .on(DSL.field(DSL.name("p3", "id"), UUID.class)
                        .eq(DSL.field(DSL.name("i2", "product_id"), UUID.class)))
                .where(DSL.and(
                        DSL.field(DSL.name("p1", "id"), UUID.class)
                                .eq(DSL.field(DSL.name("i0", "customer"), UUID.class)),
                        condition
                )));

        var result = joins.collect(condition);

        assertEquals(expected, result);
    }

    @Test
    void multipleTerms_selfReferencingRelations() {
        var joins = new JoinCollection(INVOICE.getTable());
        var condition = DSL.condition(true);

        joins.addRelation(INVOICE_PREVIOUS); // left term
        joins.resetCurrentTable();
        joins.addRelation(INVOICE_NEXT); // right term

        var expected = DSL.exists(DSL.selectOne()
                .from(DSL.table("invoice").as("i1"))
                .join(DSL.table("invoice").as("i2"))
                .on(DSL.field(DSL.name("i2", "previous_invoice"), UUID.class)
                        .eq(DSL.field(DSL.name("i0", "id"), UUID.class)))
                .where(DSL.and(
                        DSL.field(DSL.name("i1", "id"), UUID.class)
                                .eq(DSL.field(DSL.name("i0", "previous_invoice"), UUID.class)),
                        condition
                )));

        var result = joins.collect(condition);

        assertEquals(expected, result);
    }

    @Test
    void addRelationTest_illegalRelation() {
        var joins = new JoinCollection(INVOICE.getTable());
        assertThrows(IllegalArgumentException.class, () -> joins.addRelation(PERSON_INVOICES));
        joins.addRelation(INVOICE_CUSTOMER);
        assertThrows(IllegalArgumentException.class, () -> joins.addRelation(INVOICE_NEXT));
    }
}