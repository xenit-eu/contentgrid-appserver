package com.contentgrid.appserver.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter.Operation;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.exception.InvalidFilterParameterException;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison.ContentGridPrefixSearch;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.FunctionExpression.Operator;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SetValue;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.predicates.model.Variable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThunkExpressionGeneratorTest {

    private static final SimpleAttribute LONG_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("count"))
            .column(ColumnName.of("count"))
            .type(Type.LONG)
            .build();

    private static final SimpleAttribute DOUBLE_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("price"))
            .column(ColumnName.of("price"))
            .type(Type.DOUBLE)
            .build();

    private static final SimpleAttribute BOOLEAN_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("in_stock"))
            .column(ColumnName.of("in_stock"))
            .type(Type.BOOLEAN)
            .build();

    private static final SimpleAttribute TEXT_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("description"))
            .column(ColumnName.of("description"))
            .type(Type.TEXT)
            .build();

    private static final SimpleAttribute DATE_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("event_date"))
            .column(ColumnName.of("event_date"))
            .type(Type.DATE)
            .build();

    private static final SimpleAttribute DATETIME_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("arrival_timestamp"))
            .column(ColumnName.of("arrival_timestamp"))
            .type(Type.DATETIME)
            .build();

    private static final SimpleAttribute UUID_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("invoice_id"))
            .column(ColumnName.of("invoiced_id"))
            .type(Type.UUID)
            .flag(ReadOnlyFlag.INSTANCE)
            .build();

    private static final CompositeAttribute COMP_ATTR = CompositeAttributeImpl.builder()
            .name(AttributeName.of("audit"))
            .attribute(SimpleAttribute.builder()
                    .name(AttributeName.of("modified_at"))
                    .column(ColumnName.of("audit__modified_at"))
                    .type(Type.DATETIME)
                    .build())
            .attribute(CompositeAttributeImpl.builder()
                    .name(AttributeName.of("modified_by"))
                    .attribute(SimpleAttribute.builder()
                            .name(AttributeName.of("name"))
                            .column(ColumnName.of("audit__modified_by__name"))
                            .type(Type.TEXT)
                            .build()
                    )
                    .attribute(SimpleAttribute.builder()
                            .name(AttributeName.of("id"))
                            .column(ColumnName.of("audit__modified_by__id"))
                            .type(Type.UUID)
                            .build()
                    )
                    .build())
            .build();

    private static final Entity testEntity = Entity.builder()
            .name(EntityName.of("testEntity"))
            .table(TableName.of("test_entity"))
            .pathSegment(PathSegmentName.of("test-entities"))
            .linkName(LinkName.of("test-entities"))
            .primaryKey(UUID_ATTR)
            .attribute(LONG_ATTR)
            .attribute(DOUBLE_ATTR)
            .attribute(BOOLEAN_ATTR)
            .attribute(TEXT_ATTR)
            .attribute(DATE_ATTR)
            .attribute(DATETIME_ATTR)
            .attribute(COMP_ATTR)
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("count"))
                    .attribute(LONG_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.GREATER_THAN)
                    .name(FilterName.of("count~gt"))
                    .attribute(LONG_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.GREATER_THAN_OR_EQUAL)
                    .name(FilterName.of("count~gte"))
                    .attribute(LONG_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.LESS_THAN)
                    .name(FilterName.of("count~lt"))
                    .attribute(LONG_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.LESS_THAN_OR_EQUAL)
                    .name(FilterName.of("count~lte"))
                    .attribute(LONG_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("price"))
                    .attribute(DOUBLE_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.GREATER_THAN)
                    .name(FilterName.of("price~gt"))
                    .attribute(DOUBLE_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.GREATER_THAN_OR_EQUAL)
                    .name(FilterName.of("price~gte"))
                    .attribute(DOUBLE_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.LESS_THAN)
                    .name(FilterName.of("price~lt"))
                    .attribute(DOUBLE_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.LESS_THAN_OR_EQUAL)
                    .name(FilterName.of("price~lte"))
                    .attribute(DOUBLE_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("in_stock"))
                    .attribute(BOOLEAN_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("description"))
                    .attribute(TEXT_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.PREFIX)
                    .name(FilterName.of("description~prefix"))
                    .attribute(TEXT_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("event_date"))
                    .attribute(DATE_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.GREATER_THAN)
                    .name(FilterName.of("event_date~after"))
                    .attribute(DATE_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.GREATER_THAN_OR_EQUAL)
                    .name(FilterName.of("event_date~from"))
                    .attribute(DATE_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.LESS_THAN)
                    .name(FilterName.of("event_date~before"))
                    .attribute(DATE_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.LESS_THAN_OR_EQUAL)
                    .name(FilterName.of("event_date~to"))
                    .attribute(DATE_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("arrival_timestamp"))
                    .attribute(DATETIME_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.GREATER_THAN)
                    .name(FilterName.of("arrival_timestamp~after"))
                    .attribute(DATETIME_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.GREATER_THAN_OR_EQUAL)
                    .name(FilterName.of("arrival_timestamp~from"))
                    .attribute(DATETIME_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.LESS_THAN)
                    .name(FilterName.of("arrival_timestamp~before"))
                    .attribute(DATETIME_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.LESS_THAN_OR_EQUAL)
                    .name(FilterName.of("arrival_timestamp~to"))
                    .attribute(DATETIME_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("id"))
                    .attribute(UUID_ATTR)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("audit.modified_by.name"))
                    .attributePath(PropertyPath.toAttribute(AttributeName.of("audit"), AttributeName.of("modified_by"), AttributeName.of("name")))
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("shipment.destination"))
                    .attributePath(PropertyPath.toAttributeUnchecked(RelationName.of("shipment"), AttributeName.of("destination")))
                    .build())
            .build();

    private static final Entity shipmentEntity = Entity.builder()
            .name(EntityName.of("shipment"))
            .table(TableName.of("shipment"))
            .pathSegment(PathSegmentName.of("shipments"))
            .linkName(LinkName.of("shipments"))
            .attribute(SimpleAttribute.builder()
                    .name(AttributeName.of("shipped_on"))
                    .column(ColumnName.of("shipped_on"))
                    .type(Type.DATETIME)
                    .build()
            )
            .attribute(SimpleAttribute.builder()
                    .name(AttributeName.of("destination"))
                    .column(ColumnName.of("destination"))
                    .type(Type.TEXT)
                    .build()
            )
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("shipped_on"))
                    .attribute(SimpleAttribute.builder()
                            .name(AttributeName.of("shipped_on"))
                            .column(ColumnName.of("shipped_on"))
                            .type(Type.DATETIME)
                            .build())
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("destination"))
                    .attribute(SimpleAttribute.builder()
                            .name(AttributeName.of("destination"))
                            .column(ColumnName.of("destination"))
                            .type(Type.TEXT)
                            .build())
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("parcel.barcode"))
                    .attributePath(PropertyPath.toAttributeUnchecked(RelationName.of("parcel"), AttributeName.of("barcode")))
                    .build())
            .build();

    private static final SimpleAttribute barcode = SimpleAttribute.builder()
            .name(AttributeName.of("barcode"))
            .column(ColumnName.of("barcode"))
            .type(Type.TEXT)
            .build();

    private static final Entity parcelEntity = Entity.builder()
            .name(EntityName.of("person"))
            .table(TableName.of("person"))
            .pathSegment(PathSegmentName.of("person"))
            .linkName(LinkName.of("person"))
            .attribute(barcode)
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("barcode"))
                    .attribute(barcode)
                    .build())
            .build();

    private static final SimpleAttribute customerName = SimpleAttribute.builder()
            .name(AttributeName.of("name"))
            .column(ColumnName.of("name"))
            .type(Type.TEXT)
            .build();
    private static final Entity customerEntity = Entity.builder()
            .name(EntityName.of("customer"))
            .table(TableName.of("customer"))
            .pathSegment(PathSegmentName.of("customers"))
            .linkName(LinkName.of("customers"))
            .attribute(customerName)
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("name"))
                    .attribute(customerName)
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("shipments.destination"))
                    .attributePath(PropertyPath.toAttributeUnchecked(RelationName.of("shipments"), AttributeName.of("destination")))
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("wishlist.description"))
                    .attributePath(PropertyPath.toAttributeUnchecked(RelationName.of("wishlist"), AttributeName.of("description")))
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.PREFIX)
                    .name(FilterName.of("wishlist.description~prefix"))
                    .attributePath(PropertyPath.toAttributeUnchecked(RelationName.of("wishlist"), AttributeName.of("description")))
                    .build())
            .build();

    private static final Relation shipmentRelation = ManyToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(testEntity.getName())
                    .name(RelationName.of("shipment"))
                    .pathSegment(PathSegmentName.of("shipment"))
                    .linkName(LinkName.of("shipment"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(shipmentEntity.getName())
                    .name(RelationName.of("products"))
                    .pathSegment(PathSegmentName.of("products"))
                    .linkName(LinkName.of("products"))
                    .build())
            .targetReference(ColumnName.of("shipment"))
            .build();

    private static final Relation parcelRelation = SourceOneToOneRelation.builder()
            .sourceEndPoint(Relation.RelationEndPoint.builder()
                    .entity(shipmentEntity.getName())
                    .name(RelationName.of("parcel"))
                    .pathSegment(PathSegmentName.of("parcel"))
                    .linkName(LinkName.of("parcel"))
                    .build())
            .targetEndPoint(Relation.RelationEndPoint.builder()
                    .entity(parcelEntity.getName())
                    .name(RelationName.of("shipment"))
                    .pathSegment(PathSegmentName.of("shipment"))
                    .linkName(LinkName.of("shipment"))
                    .build())
            .targetReference(ColumnName.of("parcel"))
            .build();

    private static final Relation customerShipmentRelation = OneToManyRelation.builder()
            .sourceEndPoint(Relation.RelationEndPoint.builder()
                    .entity(customerEntity.getName())
                    .name(RelationName.of("shipments"))
                    .pathSegment(PathSegmentName.of("shipments"))
                    .linkName(LinkName.of("shipments"))
                    .build())
            .targetEndPoint(Relation.RelationEndPoint.builder()
                    .entity(shipmentEntity.getName())
                    .name(RelationName.of("customer"))
                    .pathSegment(PathSegmentName.of("customer"))
                    .linkName(LinkName.of("customer"))
                    .build())
            .sourceReference(ColumnName.of("customer"))
            .build();

    private static final Relation wishlistRelation = ManyToManyRelation.builder()
            .sourceEndPoint(Relation.RelationEndPoint.builder()
                    .entity(customerEntity.getName())
                    .name(RelationName.of("wishlist"))
                    .pathSegment(PathSegmentName.of("wishlist"))
                    .linkName(LinkName.of("wishlist"))
                    .build())
            .targetEndPoint(Relation.RelationEndPoint.builder()
                    .entity(testEntity.getName())
                    .name(RelationName.of("wishlisted_by"))
                    .pathSegment(PathSegmentName.of("wishlisted-by"))
                    .linkName(LinkName.of("wishlisted-by"))
                    .build())
            .joinTable(TableName.of("customer_wishlist"))
            .sourceReference(ColumnName.of("customer"))
            .targetReference(ColumnName.of("test_entity"))
            .build();

    private static final Application testApplication = Application.builder()
            .name(ApplicationName.of("testApplication"))
            .entity(testEntity)
            .entity(shipmentEntity)
            .entity(parcelEntity)
            .entity(customerEntity)
            .relation(shipmentRelation)
            .relation(parcelRelation)
            .relation(customerShipmentRelation)
            .relation(wishlistRelation)
            .build();

    @Test
    void emptyParamsShouldReturnTrueExpression() {
        Map<String, List<String>> params = new HashMap<>();
        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        assertInstanceOf(Scalar.class, result);
        assertEquals(true, ((Scalar<Boolean>) result).getValue());
    }

    @Test
    void unknownFilterShouldBeIgnored() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("nonExistentFilter", List.of("value"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        assertInstanceOf(Scalar.class, result);
        assertEquals(true, ((Scalar<Boolean>) result).getValue());
    }

    @Test
    void paramsWithoutValuesShouldBeIgnored() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("description~prefix", List.of());
        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        assertInstanceOf(Scalar.class, result);
        assertEquals(true, ((Scalar<Boolean>) result).getValue());
    }

    @Test
    void singleParameterSingleValueShouldCreateEqualityExpression() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("description~prefix", List.of("test value"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        var comparison = assertInstanceOf(ContentGridPrefixSearch.class, result);
        assertEquals("test value", ((Scalar<String>) comparison.getRightTerm()).getValue());
    }

    @Test
    void multipleParametersSingleValueShouldCreateConjunction() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("description~prefix", List.of("test value"));
        params.put("count", List.of("123"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        var operation = assertInstanceOf(LogicalOperation.class, result);
        assertEquals(Operator.AND, operation.getOperator());
        assertEquals(2, operation.getTerms().size());
        operation.getTerms().forEach(term -> {
            assertInstanceOf(Comparison.class, term);
        });
    }

    @Test
    void singleParameterMultipleValuesShouldCreateDisjunction() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("description~prefix", List.of("foo", "bar"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        var operation = assertInstanceOf(LogicalOperation.class, result);
        assertEquals(Operator.OR, operation.getOperator());
        assertEquals(2, operation.getTerms().size());
        operation.getTerms().forEach(term -> {
            assertInstanceOf(ContentGridPrefixSearch.class, term);
        });
    }

    @Test
    void multipleParametersMultipleValuesShouldCreateConjunctionWithOptimizedTerms() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("description~prefix", List.of("foo", "bar"));
        params.put("count", List.of("0", "1"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        var operation = assertInstanceOf(LogicalOperation.class, result);
        assertEquals(Operator.AND, operation.getOperator());
        assertEquals(2, operation.getTerms().size());

        // PREFIX uses a disjunction (no single-expression optimization available)
        var prefixTerm = operation.getTerms().stream()
                .filter(t -> t instanceof LogicalOperation lo && lo.getOperator() == Operator.OR)
                .findFirst().orElseThrow();
        var orOp = assertInstanceOf(LogicalOperation.class, prefixTerm);
        assertEquals(2, orOp.getTerms().size());
        orOp.getTerms().forEach(t -> assertInstanceOf(ContentGridPrefixSearch.class, t));

        // EXACT with multiple values uses IN
        var inTerm = operation.getTerms().stream()
                .filter(t -> t instanceof Comparison c && c.getOperator() == Operator.IN)
                .findFirst().orElseThrow();
        var inComparison = assertInstanceOf(Comparison.class, inTerm);
        var setValue = assertInstanceOf(SetValue.class, inComparison.getRightTerm());
        assertEquals(2, setValue.getValue().size());
    }

    @ParameterizedTest
    @CsvSource({
            "count,EQUALS",
            "count~gt,GREATER_THAN",
            "count~gte,GREATER_THAN_OR_EQUAL_TO",
            "count~lt,LESS_THAN",
            "count~lte,LESS_THEN_OR_EQUAL_TO",
    })
    void longAttributeShouldParseCorrectly(String name, Operator operator) {
        Map<String, List<String>> params = new HashMap<>();
        params.put(name, List.of("123"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals(new BigDecimal("123"), ((Scalar<?>) comparison.getRightTerm()).getValue());
        assertEquals(operator, comparison.getOperator());
    }

    @ParameterizedTest
    @CsvSource({
            "price,EQUALS",
            "price~gt,GREATER_THAN",
            "price~gte,GREATER_THAN_OR_EQUAL_TO",
            "price~lt,LESS_THAN",
            "price~lte,LESS_THEN_OR_EQUAL_TO",
    })
    void doubleAttributeShouldParseCorrectly(String name, Operator operator) {
        Map<String, List<String>> params = new HashMap<>();
        params.put(name, List.of("123.45"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals(new BigDecimal("123.45"), ((Scalar<?>) comparison.getRightTerm()).getValue());
        assertEquals(operator, comparison.getOperator());
    }

    @Test
    void booleanAttributeShouldParseCorrectly() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("in_stock", List.of("true"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals(true, ((Scalar<?>) comparison.getRightTerm()).getValue());
    }

    @ParameterizedTest
    @CsvSource({
            "description,EQUALS",
            "description~prefix,CUSTOM",
    })
    void textAttributeShouldParseCorrectly(String name, Operator operator) {
        Map<String, List<String>> params = new HashMap<>();
        params.put(name, List.of("sample text"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals("sample text", ((Scalar<?>) comparison.getRightTerm()).getValue());
        assertEquals(operator, comparison.getOperator());
    }

    @ParameterizedTest
    @CsvSource({
            "event_date,EQUALS",
            "event_date~after,GREATER_THAN",
            "event_date~from,GREATER_THAN_OR_EQUAL_TO",
            "event_date~before,LESS_THAN",
            "event_date~to,LESS_THEN_OR_EQUAL_TO",
    })
    void localDateAttributeShouldParseCorrectly(String name, Operator operator) {
        String date = "2023-01-01";
        Map<String, List<String>> params = new HashMap<>();
        params.put(name, List.of(date));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals(LocalDate.parse(date), ((Scalar<?>) comparison.getRightTerm()).getValue());
        assertEquals(operator, comparison.getOperator());
    }

    @ParameterizedTest
    @CsvSource({
            "arrival_timestamp,EQUALS",
            "arrival_timestamp~after,GREATER_THAN",
            "arrival_timestamp~from,GREATER_THAN_OR_EQUAL_TO",
            "arrival_timestamp~before,LESS_THAN",
            "arrival_timestamp~to,LESS_THEN_OR_EQUAL_TO",
    })
    void datetimeAttributeShouldParseCorrectly(String name, Operator operator) {
        String timestamp = "2023-01-01T12:00:00Z";
        Map<String, List<String>> params = new HashMap<>();
        params.put(name, List.of(timestamp));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals(Instant.parse(timestamp), ((Scalar<?>) comparison.getRightTerm()).getValue());
        assertEquals(operator, comparison.getOperator());
    }

    @Test
    void uuidAttributeShouldParseCorrectly() {
        UUID uuid = UUID.randomUUID();
        Map<String, List<String>> params = new HashMap<>();
        params.put("id", List.of(uuid.toString()));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals(uuid, ((Scalar<?>) comparison.getRightTerm()).getValue());
    }

    @Test
    void invalidLongValueShouldThrowException() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("count", List.of("not a number"));

        InvalidFilterParameterException exception = assertThrows(
                InvalidFilterParameterException.class,
                () -> ThunkExpressionGenerator.from(testApplication, testEntity, params)
        );

        assertEquals("count", exception.getFilterName().getValue());
        assertEquals(Type.LONG, exception.getType());
    }

    @Test
    void invalidDoubleValueShouldThrowException() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("price", List.of("not a decimal"));

        InvalidFilterParameterException exception = assertThrows(
                InvalidFilterParameterException.class,
                () -> ThunkExpressionGenerator.from(testApplication, testEntity, params)
        );

        assertEquals("price", exception.getFilterName().getValue());
        assertEquals(Type.DOUBLE, exception.getType());
    }

    @ParameterizedTest
    @CsvSource({"not a date", "2025-01-01T01:01:01.234Z"})
    void invalidLocalDateValueShouldThrowException(String value) {
        Map<String, List<String>> params = new HashMap<>();
        params.put("event_date~after", List.of(value));

        InvalidFilterParameterException exception = assertThrows(
                InvalidFilterParameterException.class,
                () -> ThunkExpressionGenerator.from(testApplication, testEntity, params)
        );

        assertEquals("event_date~after", exception.getFilterName().getValue());
        assertEquals(Type.DATE, exception.getType());
    }

    @ParameterizedTest
    @CsvSource({"not a timestamp", "2025-01-01"})
    void invalidDatetimeValueShouldThrowException(String value) {
        Map<String, List<String>> params = new HashMap<>();
        params.put("arrival_timestamp~after", List.of(value));

        InvalidFilterParameterException exception = assertThrows(
                InvalidFilterParameterException.class,
                () -> ThunkExpressionGenerator.from(testApplication, testEntity, params)
        );

        assertEquals("arrival_timestamp~after", exception.getFilterName().getValue());
        assertEquals(Type.DATETIME, exception.getType());
    }

    @Test
    void invalidUuidValueShouldThrowException() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("id", List.of("not a uuid"));

        InvalidFilterParameterException exception = assertThrows(
                InvalidFilterParameterException.class,
                () -> ThunkExpressionGenerator.from(testApplication, testEntity, params)
        );

        assertEquals("id", exception.getFilterName().getValue());
        assertEquals(Type.UUID, exception.getType());
    }

    @Test
    void emptyValueIsValidString() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("description", List.of(""));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertInstanceOf(Scalar.class, comparison.getRightTerm());
        assertEquals("", ((Scalar<?>) comparison.getRightTerm()).getValue());
    }

    @Test
    void compositeAttributeShouldBeFoundCorrectly() {
        Map<String, List<String>> params = Map.of("audit.modified_by.name", List.of("Alice Aaronson"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertInstanceOf(Scalar.class, comparison.getRightTerm());
        assertEquals(
                SymbolicReference.of(
                        Variable.named("entity"),
                        SymbolicReference.path("audit"),
                        SymbolicReference.path("modified_by"),
                        SymbolicReference.path("name")
                ),
                comparison.getLeftTerm()
        );
    }

    @Test
    void acrossManyToOneRelationAttributeIsValid() {
        Map<String, List<String>> params = Map.of("shipment.destination", List.of("North Pole"));

        var entity = testApplication.getEntityByName(EntityName.of("testEntity")).orElseThrow();

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, entity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertInstanceOf(Scalar.class, comparison.getRightTerm());
        assertEquals(
                SymbolicReference.of(
                        Variable.named("entity"),
                        SymbolicReference.path("shipment"),
                        SymbolicReference.path("destination")
                ),
                comparison.getLeftTerm()
        );

        assertEquals(Scalar.of("North Pole"), comparison.getRightTerm());
    }

    @Test
    void acrossOneToOneRelationAttributeIsValid() {
        Map<String, List<String>> params = Map.of("parcel.barcode", List.of("1234567890"));
        var entity = testApplication.getEntityByName(EntityName.of("shipment")).orElseThrow();
        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, entity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertInstanceOf(Scalar.class, comparison.getRightTerm());
        assertEquals(
                SymbolicReference.of(
                        Variable.named("entity"),
                        SymbolicReference.path("parcel"),
                        SymbolicReference.path("barcode")
                ),
                comparison.getLeftTerm()
        );
        assertEquals(Scalar.of("1234567890"), comparison.getRightTerm());
    }

    @Test
    void acrossOneToManyRelationAttributeIsValid() {
        Map<String, List<String>> params = Map.of("shipments.destination", List.of("Moon Base"));
        var entity = testApplication.getEntityByName(EntityName.of("customer")).orElseThrow();
        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, entity, params);
        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertInstanceOf(Scalar.class, comparison.getRightTerm());
        assertEquals(
                SymbolicReference.of(
                        Variable.named("entity"),
                        SymbolicReference.path("shipments"),
                        SymbolicReference.pathVar("__wildcard_0"),
                        SymbolicReference.path("destination")
                ),
                comparison.getLeftTerm()
        );
        assertEquals(Scalar.of("Moon Base"), comparison.getRightTerm());
    }

    @Test
    void acrossManyToManyRelationAttributeIsValid() {
        Map<String, List<String>> params = Map.of("wishlist.description", List.of("A unicorn"));
        var entity = testApplication.getEntityByName(EntityName.of("customer")).orElseThrow();
        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, entity, params);
        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertInstanceOf(Scalar.class, comparison.getRightTerm());
        assertEquals(
                SymbolicReference.of(
                        Variable.named("entity"),
                        SymbolicReference.path("wishlist"),
                        SymbolicReference.pathVar("__wildcard_0"),
                        SymbolicReference.path("description")
                ),
                comparison.getLeftTerm()
        );
        assertEquals(Scalar.of("A unicorn"), comparison.getRightTerm());
    }

    @Test
    void multipleToManyRelationsUseDifferentWildcards() {
        Map<String, List<String>> params = new TreeMap<>();
        params.put("shipments.destination", List.of("Moon Base"));
        params.put("wishlist.description", List.of("A unicorn"));
        var entity = testApplication.getEntityByName(EntityName.of("customer")).orElseThrow();
        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, entity, params);
        var logicalOperation = assertInstanceOf(LogicalOperation.class, result);
        assertEquals(Operator.AND, logicalOperation.getOperator());
        assertEquals(2, logicalOperation.getTerms().size());
        var left = assertInstanceOf(Comparison.class, logicalOperation.getTerms().getFirst());
        var right = assertInstanceOf(Comparison.class, logicalOperation.getTerms().getLast());

        assertEquals(
                SymbolicReference.of(
                        Variable.named("entity"),
                        SymbolicReference.path("shipments"),
                        SymbolicReference.pathVar("__wildcard_0"),
                        SymbolicReference.path("destination")
                ),
                left.getLeftTerm()
        );
        assertEquals(
                SymbolicReference.of(
                        Variable.named("entity"),
                        SymbolicReference.path("wishlist"),
                        SymbolicReference.pathVar("__wildcard_1"),
                        SymbolicReference.path("description")
                ),
                right.getLeftTerm()
        );
    }

    @Test
    void multipleValuesExactShouldUseIn() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("count", List.of("1", "2", "3"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        var comparison = assertInstanceOf(Comparison.class, result);
        assertEquals(Operator.IN, comparison.getOperator());
        var setValue = assertInstanceOf(SetValue.class, comparison.getRightTerm());
        assertEquals(3, setValue.getValue().size());
        assertTrue(setValue.getValue().contains(Scalar.of(1L)));
        assertTrue(setValue.getValue().contains(Scalar.of(2L)));
        assertTrue(setValue.getValue().contains(Scalar.of(3L)));
    }

    @Test
    void multipleValuesGreaterThanShouldUseMinimum() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("count~gt", List.of("10", "5", "20"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        var comparison = assertInstanceOf(Comparison.class, result);
        assertEquals(Operator.GREATER_THAN, comparison.getOperator());
        assertEquals(new BigDecimal("5"), ((Scalar<?>) comparison.getRightTerm()).getValue());
    }

    @Test
    void multipleValuesLessThanShouldUseMaximum() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("count~lt", List.of("10", "5", "20"));

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, testEntity, params);

        var comparison = assertInstanceOf(Comparison.class, result);
        assertEquals(Operator.LESS_THAN, comparison.getOperator());
        assertEquals(new BigDecimal("20"), ((Scalar<?>) comparison.getRightTerm()).getValue());
    }

    @Test
    void multipleExactValuesToManyRelationUsesInWithSingleWildcard() {
        Map<String, List<String>> params = Map.of(
                "shipments.destination", List.of("Moon Base", "Middle Earth")
        );
        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, customerEntity, params);

        var comparison = assertInstanceOf(Comparison.class, result);
        assertEquals(Operator.IN, comparison.getOperator());
        assertEquals(
                SymbolicReference.of(
                        Variable.named("entity"),
                        SymbolicReference.path("shipments"),
                        SymbolicReference.pathVar("__wildcard_0"),
                        SymbolicReference.path("destination")
                ),
                comparison.getLeftTerm()
        );
        var setValue = assertInstanceOf(SetValue.class, comparison.getRightTerm());
        assertTrue(setValue.getValue().contains(Scalar.of("Moon Base")));
        assertTrue(setValue.getValue().contains(Scalar.of("Middle Earth")));
    }

    @Test
    void multiplePrefixValuesToManyRelationUseDifferentWildCards() {
        Map<String, List<String>> params = Map.of(
                "wishlist.description~prefix", List.of("foo", "bar")
        );
        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testApplication, customerEntity, params);
        var logicalOperation = assertInstanceOf(LogicalOperation.class, result);
        assertEquals(Operator.OR, logicalOperation.getOperator());
        assertEquals(2, logicalOperation.getTerms().size());
        var left = assertInstanceOf(Comparison.class, logicalOperation.getTerms().getFirst());
        var right = assertInstanceOf(Comparison.class, logicalOperation.getTerms().getLast());

        assertEquals(
                SymbolicReference.of(
                        Variable.named("entity"),
                        SymbolicReference.path("wishlist"),
                        SymbolicReference.pathVar("__wildcard_0"),
                        SymbolicReference.path("description")
                ),
                left.getLeftTerm()
        );
        assertEquals(
                SymbolicReference.of(
                        Variable.named("entity"),
                        SymbolicReference.path("wishlist"),
                        SymbolicReference.pathVar("__wildcard_1"),
                        SymbolicReference.path("description")
                ),
                right.getLeftTerm()
        );
    }
}