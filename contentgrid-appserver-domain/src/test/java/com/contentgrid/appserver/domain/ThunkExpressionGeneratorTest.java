package com.contentgrid.appserver.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.exception.InvalidParameterException;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.FunctionExpression.Operator;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    private static final SimpleAttribute DATETIME_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("arrival_date"))
            .column(ColumnName.of("arrival_date"))
            .type(Type.DATETIME)
            .build();

    private static final SimpleAttribute UUID_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("id"))
            .column(ColumnName.of("id"))
            .type(Type.UUID)
            .build();

    private static final Entity testEntity = Entity.builder()
                .name(EntityName.of("testEntity"))
                .table(TableName.of("test_entity"))
                .pathSegment(PathSegmentName.of("test-entities"))
                .primaryKey(UUID_ATTR)
                .attribute(LONG_ATTR)
                .attribute(DOUBLE_ATTR)
                .attribute(BOOLEAN_ATTR)
                .attribute(TEXT_ATTR)
                .attribute(DATETIME_ATTR)
                .build();

    @Test
    void emptyParamsShouldReturnTrueExpression() {
        Map<String, String> params = new HashMap<>();
        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testEntity, params);

        assertInstanceOf(Scalar.class, result);
        assertEquals(true, ((Scalar<Boolean>) result).getValue());
    }

    @Test
    void unknownAttributeShouldBeIgnored() {
        Map<String, String> params = new HashMap<>();
        params.put("nonExistentAttr", "value");

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testEntity, params);

        assertInstanceOf(Scalar.class, result);
        assertEquals(true, ((Scalar<Boolean>) result).getValue());
    }

    @Test
    void singleValidParameterShouldCreateEqualityExpression() {
        Map<String, String> params = new HashMap<>();
        params.put("description", "test value");

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals("test value", ((Scalar<String>) comparison.getRightTerm()).getValue());
    }

    @Test
    void multipleValidParametersShouldCreateConjunction() {
        Map<String, String> params = new HashMap<>();
        params.put("description", "test value");
        params.put("count", "123");

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testEntity, params);

        assertInstanceOf(LogicalOperation.class, result);
        LogicalOperation operation = (LogicalOperation) result;
        assertEquals(Operator.AND, operation.getOperator());
        assertEquals(2, operation.getTerms().size());
    }

    @Test
    void longAttributeShouldParseCorrectly() {
        Map<String, String> params = new HashMap<>();
        params.put("count", "123");

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals(new BigDecimal("123"), ((Scalar<?>) comparison.getRightTerm()).getValue());
    }

    @Test
    void doubleAttributeShouldParseCorrectly() {
        Map<String, String> params = new HashMap<>();
        params.put("price", "123.45");

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals(new BigDecimal("123.45"), ((Scalar<?>) comparison.getRightTerm()).getValue());
    }

    @Test
    void booleanAttributeShouldParseCorrectly() {
        Map<String, String> params = new HashMap<>();
        params.put("in_stock", "true");

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals(true, ((Scalar<?>) comparison.getRightTerm()).getValue());
    }

    @Test
    void textAttributeShouldParseCorrectly() {
        Map<String, String> params = new HashMap<>();
        params.put("description", "sample text");

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals("sample text", ((Scalar<?>) comparison.getRightTerm()).getValue());
    }

    @Test
    void datetimeAttributeShouldParseCorrectly() {
        String timestamp = "2023-01-01T12:00:00Z";
        Map<String, String> params = new HashMap<>();
        params.put("arrival_date", timestamp);

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals(Instant.parse(timestamp), ((Scalar<?>) comparison.getRightTerm()).getValue());
    }

    @Test
    void uuidAttributeShouldParseCorrectly() {
        UUID uuid = UUID.randomUUID();
        Map<String, String> params = new HashMap<>();
        params.put("id", uuid.toString());

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertEquals(uuid, ((Scalar<?>) comparison.getRightTerm()).getValue());
    }

    @Test
    void invalidLongValueShouldThrowException() {
        Map<String, String> params = new HashMap<>();
        params.put("count", "not a number");

        InvalidParameterException exception = assertThrows(
                InvalidParameterException.class,
                () -> ThunkExpressionGenerator.from(testEntity, params)
        );

        assertEquals("count", exception.getAttributeName());
        assertEquals(Type.LONG, exception.getType());
        assertEquals("not a number", exception.getValue());
    }

    @Test
    void invalidDoubleValueShouldThrowException() {
        Map<String, String> params = new HashMap<>();
        params.put("price", "not a decimal");

        InvalidParameterException exception = assertThrows(
                InvalidParameterException.class,
                () -> ThunkExpressionGenerator.from(testEntity, params)
        );

        assertEquals("price", exception.getAttributeName());
        assertEquals(Type.DOUBLE, exception.getType());
        assertEquals("not a decimal", exception.getValue());
    }

    @Test
    void invalidDatetimeValueShouldThrowException() {
        Map<String, String> params = new HashMap<>();
        params.put("arrival_date", "not a date");

        InvalidParameterException exception = assertThrows(
                InvalidParameterException.class,
                () -> ThunkExpressionGenerator.from(testEntity, params)
        );

        assertEquals("arrival_date", exception.getAttributeName());
        assertEquals(Type.DATETIME, exception.getType());
        assertEquals("not a date", exception.getValue());
    }

    @Test
    void invalidUuidValueShouldThrowException() {
        Map<String, String> params = new HashMap<>();
        params.put("id", "not a uuid");

        InvalidParameterException exception = assertThrows(
                InvalidParameterException.class,
                () -> ThunkExpressionGenerator.from(testEntity, params)
        );

        assertEquals("id", exception.getAttributeName());
        assertEquals(Type.UUID, exception.getType());
        assertEquals("not a uuid", exception.getValue());
    }

    @Test
    void emptyValueIsValidString() {
        Map<String, String> params = new HashMap<>();
        params.put("description", "");

        ThunkExpression<Boolean> result = ThunkExpressionGenerator.from(testEntity, params);

        assertInstanceOf(Comparison.class, result);
        Comparison comparison = (Comparison) result;
        assertInstanceOf(Scalar.class, comparison.getRightTerm());
        assertEquals("", ((Scalar<?>) comparison.getRightTerm()).getValue());
    }
}