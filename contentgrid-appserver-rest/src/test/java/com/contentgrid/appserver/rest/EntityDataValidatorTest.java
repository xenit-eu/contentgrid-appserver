package com.contentgrid.appserver.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.rest.exception.InvalidEntityDataException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EntityDataValidatorTest {

    private static final Entity INVOICE = Entity.builder()
            .name(EntityName.of("Invoice"))
            .table(TableName.of("invoice"))
            .pathSegment(PathSegmentName.of("invoices"))
            .linkName(LinkName.of("invoices"))
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
                    .linkName(LinkName.of("content"))
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

    private static final Application application = Application.builder()
            .name(ApplicationName.of("EntityValidatorTestApp"))
            .entity(INVOICE)
            .build();

    @Test
    void testAllCorrect() {
        var converted = EntityDataValidator.validate(INVOICE, Map.of(
                "internal_ref", "636c5925-dcac-4ccd-8b46-d0489dff7e48",
                "invoice_number", "2398475-BD",
                "total_amount", 129.95,
                "is_paid", false,
                "num_items", 3,
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

    @Nested
    class CompositeTest {

        Attribute attribute = CompositeAttributeImpl.builder()
                .name(AttributeName.of("address"))
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("country"))
                        .column(ColumnName.of("address__country"))
                        .type(Type.TEXT)
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("zip"))
                        .column(ColumnName.of("address__zip"))
                        .type(Type.TEXT)
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("street"))
                        .column(ColumnName.of("address__street"))
                        .type(Type.TEXT)
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("number"))
                        .column(ColumnName.of("address__number"))
                        .type(Type.LONG)
                        .build())
                .build();

        Entity entity = Entity.builder()
                .name(EntityName.of("thing"))
                .pathSegment(PathSegmentName.of("thing"))
                .linkName(LinkName.of("thing"))
                .table(TableName.of("thing"))
                .attribute(attribute)
                .build();

        @Test
        void testCompositeCorrect() {

            EntityDataValidator.validate(entity, Map.of(
                    "address", Map.of(
                            "country", "TEST",
                            "zip", "0123",
                            "street", "EntityDataValidatorTest",
                            "number", 0
                    )
            ));
        }

        @Test
        void testCompositeSubError() {

            var exception = assertThrows(InvalidEntityDataException.class, () ->
                    EntityDataValidator.validate(entity, Map.of(
                            "address", Map.of(
                                    "country", "TEST",
                                    "zip", "0123",
                                    "street", "EntityDataValidatorTest",
                                    "number", "not an int"
                            )
                    )));
            assertEquals(1, exception.getInvalidAttributes().size());
            assertTrue(exception.getValidationErrors().containsKey("address.number"));
        }
    }

    @Nested
    class ContentTest {

        @Test
        void testFilenameAndMimetype() {
            EntityDataValidator.validate(INVOICE, Map.of(
                    "content", Map.of(
                            "filename", "voynich.pdf",
                            "mimetype", "application/pdf"
                    )
            ));
        }

        @Test
        void testInvalidMimetype() {
            var exception = assertThrows(InvalidEntityDataException.class, () ->
                    EntityDataValidator.validate(INVOICE, Map.of(
                            "content", Map.of(
                                    "filename", "voynich.pdf",
                                    "mimetype", "oops"
                            )
                    ))
            );

            assertTrue(exception.getValidationErrors().containsKey("content.mimetype"));
        }
    }

}