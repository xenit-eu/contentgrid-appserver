package com.contentgrid.appserver.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.rest.exception.InvalidEntityDataException;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
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
                .name(AttributeName.of("auditing"))
                .attribute(UserAttribute.builder()
                        .name(AttributeName.of("created_by"))
                        .flag(CreatorFlag.builder().build())
                        .idColumn(ColumnName.of("auditing__created_by_id"))
                        .namespaceColumn(ColumnName.of("auditing__created_by_ns"))
                        .usernameColumn(ColumnName.of("auditing__created_by_name"))
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("created_date"))
                        .column(ColumnName.of("auditing__created_date"))
                        .type(Type.DATETIME)
                        .flag(CreatedDateFlag.builder().build())
                        .build())
                .attribute(UserAttribute.builder()
                        .name(AttributeName.of("last_modified_by"))
                        .idColumn(ColumnName.of("auditing__last_modified_by_id"))
                        .namespaceColumn(ColumnName.of("auditing__last_modified_by_ns"))
                        .usernameColumn(ColumnName.of("auditing__last_modified_by_name"))
                        .flag(ModifierFlag.builder().build())
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("last_modified_date"))
                        .column(ColumnName.of("auditing__last_modified_date"))
                        .type(Type.DATETIME)
                        .flag(ModifiedDateFlag.builder().build())
                        .build())
                .build();

        Entity entity = Entity.builder()
                .name(EntityName.of("thing"))
                .pathSegment(PathSegmentName.of("thing"))
                .table(TableName.of("thing"))
                .attribute(attribute)
                .build();

        @Test
        void testCompositeCorrect() {

            EntityDataValidator.validate(entity, Map.of(
                    "auditing", Map.of(
                            "created_date", "2025-05-25T05:25:25Z",
                            "last_modified_date", "2026-06-26T06:26:26Z",
                            "created_by", Map.of(
                                    "id", "alan.smithee@example.com",
                                    "name", "Alan Smithee",
                                    "namespace", "https://keycloak.test/realms/cg-a1a1a1a1-b2b2-c3c3-d4d4-e5e5e5e5e5e5"
                            )
                    )
            ));
        }

        @Test
        void testCompositeSubError() {

            var exception = assertThrows(InvalidEntityDataException.class, () ->
                    EntityDataValidator.validate(entity, Map.of(
                            "auditing", Map.of(
                                    "created_date", "2025-05-25T05:25:25Z",
                                    "last_modified_date", "not a date"
                            )
                    )));
            assertEquals(exception.getInvalidAttributes().size(), 1);
            assertTrue(exception.getValidationErrors().containsKey("auditing.last_modified_date"));
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