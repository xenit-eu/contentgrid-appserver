package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidEntityDataException;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityDataValidatorTest {

    private final static Entity INVOICE = Entity.builder()
            .name(EntityName.of("Invoice"))
            .table(TableName.of("invoice"))
            .pathSegment(PathSegmentName.of("invoices"))
            .attribute(SimpleAttribute.builder().name(AttributeName.of("internal_ref"))
                    .column(ColumnName.of("internal_ref"))
                    .type(Type.UUID).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("invoice_number"))
                    .column(ColumnName.of("invoice_number"))
                    .type(Type.TEXT).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("total_amount"))
                    .column(ColumnName.of("amount"))
                    .type(Type.DOUBLE).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("is_paid"))
                    .column(ColumnName.of("is_paid"))
                    .type(Type.BOOLEAN).build())
            .attribute(ContentAttribute.builder().name(AttributeName.of("content"))
                    .pathSegment(PathSegmentName.of("content"))
                    .idColumn(ColumnName.of("content__id"))
                    .filenameColumn(ColumnName.of("content__filename"))
                    .mimetypeColumn(ColumnName.of("content__mimetype"))
                    .lengthColumn(ColumnName.of("content__length"))
                    .build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("num_items"))
                    .column(ColumnName.of("num_items"))
                    .type(Type.LONG).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("due_date"))
                    .column(ColumnName.of("due_date"))
                    .type(Type.DATETIME).build())
            .build();

    private final static Application application = Application.builder()
            .name(ApplicationName.of("EntityValidatorTestApp"))
            .entity(INVOICE)
            .build();

    @Test
    void testAllCorrect() {
        var converted = EntityDataValidator.validate(INVOICE, Map.of(
                "internal_ref", "636c5925-dcac-4ccd-8b46-d0489dff7e48",
                "invoice_number", "2398475-BD",
                "total_amount", "129.95",
                "is_paid", "false",
                "num_items", "3",
                "due_date", "2026-12-31T23:59:59Z"
        ));

        assertEquals(UUID.fromString("636c5925-dcac-4ccd-8b46-d0489dff7e48"), converted.get("internal_ref"));
        assertEquals("2398475-BD", converted.get("invoice_number"));
        assertEquals(129.95, converted.get("total_amount"));
        assertEquals(false, converted.get("is_paid"));
        assertEquals(3L, converted.get("num_items"));
        assertEquals(Instant.parse("2026-12-31T23:59:59Z"), converted.get("due_date"));
    }

    @Test
    void testIncorrectUUID() {
        assertThrows(InvalidEntityDataException.class, () -> EntityDataValidator.validate(INVOICE, Map.of(
                "internal_ref", "not_a_uuid"
        )));
    }

    @Test
    void testIncorrectDouble() {
        assertThrows(InvalidEntityDataException.class, () -> EntityDataValidator.validate(INVOICE, Map.of(
                "total_amount", "not_a_number"
        )));
    }

    @Test
    void testIncorrectBoolean() {
        assertThrows(InvalidEntityDataException.class, () -> EntityDataValidator.validate(INVOICE, Map.of(
                "is_paid", "not_a_boolean"
        )));
    }

    @Test
    void testIncorrectLong() {
        assertThrows(InvalidEntityDataException.class, () -> EntityDataValidator.validate(INVOICE, Map.of(
                "num_items", "not_a_long"
        )));
    }

    @Test
    void testIncorrectDatetime() {
        assertThrows(InvalidEntityDataException.class, () -> EntityDataValidator.validate(INVOICE, Map.of(
                "due_date", "not_a_date"
        )));
    }

}