package com.contentgrid.appserver.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.json.exceptions.InvalidJsonException;
import com.contentgrid.appserver.json.exceptions.UnknownFlagException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultApplicationSchemaConverterTest {

    @Test
    void testConvertSampleApplicationJson() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("sample-application.json")) {
            Application app = new DefaultApplicationSchemaConverter().convert(is);
            assertNotNull(app);
            assertEquals("HR application", app.getName().getValue());
            assertFalse(app.getEntities().isEmpty());
            assertFalse(app.getRelations().isEmpty());
            // Optionally, add more assertions for entities, attributes, and relations
        }
    }


    @Test
    void testToJsonWritesOutput() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("sample-application.json")) {
            assertNotNull(is);
            var jsonSource = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            DefaultApplicationSchemaConverter converter = new DefaultApplicationSchemaConverter();
            Application app = converter.convert(new ByteArrayInputStream(jsonSource.getBytes(StandardCharsets.UTF_8)));
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            converter.toJson(app, out);
            String jsonTarget = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertNotNull(jsonTarget);
            assertTrue(jsonTarget.contains("HR application")); // basic check for content
            assertTrue(jsonTarget.contains("entities"));
            assertTrue(jsonTarget.contains("relations"));

            assertThat(jsonTarget, sameJSONAs(jsonSource).allowingAnyArrayOrdering());
        }
    }

    @Test
    void testInvalidJson() {
        var invalidJson = "{ \"invalid\": \"json\" }";
        var converter = new DefaultApplicationSchemaConverter();
        assertThrows(InvalidJsonException.class, () -> {
            converter.convert(new ByteArrayInputStream(invalidJson.getBytes(StandardCharsets.UTF_8)));
        });
    }

    @Test
    void testUnknownFlag() {
        var jsonWithUnknownFlag = """
                {
                    "$schema": "https://contentgrid.com/schemas/application-schema.json",
                    "applicationName": "HR application",
                    "version": "1.0.0",
                    "entities": [
                        {
                            "name": "Employee",
                            "description": "An employee of the company",
                            "table": "employees",
                            "pathSegment": "employee",
                            "linkName": "employee",
                            "primaryKey":
                                {
                                    "name": "id",
                                    "description": "Unique identifier for the employee",
                                    "type": "simple",
                                    "dataType": "uuid",
                                    "columnName": "id",
                                    "flags": ["readOnly", "unknownFlag"]
                                }
                        }
                    ]
                }
                """;

        var converter = new DefaultApplicationSchemaConverter();
        assertThrows(UnknownFlagException.class, () -> converter.convert(
                new ByteArrayInputStream(jsonWithUnknownFlag.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void testTargetOneToOneSerialization() {
        var sourceEntity = getEntity("source", "source entity", "source_table");

        var targetEntity = getEntity("target", "target entity", "target_table");

        var relation = TargetOneToOneRelation.builder()
                .sourceEndPoint(Relation.RelationEndPoint.builder()
                        .entity(sourceEntity.getName())
                        .build())
                .targetEndPoint(Relation.RelationEndPoint.builder()
                        .entity(targetEntity.getName())
                        .name(RelationName.of("source"))
                        .pathSegment(PathSegmentName.of("source"))
                        .linkName(LinkName.of("source"))
                        .build())
                .sourceReference(ColumnName.of("source_ref"))
                .build();

        var app = Application.builder()
                .name(ApplicationName.of("Test Application"))
                .entities(List.of(sourceEntity, targetEntity))
                .relations(List.of(relation))
                .build();

        var converter = new DefaultApplicationSchemaConverter();

        var out = new ByteArrayOutputStream();
        converter.toJson(app, out);
        var json = out.toString(StandardCharsets.UTF_8);

        assertThat(json, sameJSONAs("""
                {
                    "$schema": "https://contentgrid.com/schemas/application-schema.json",
                    "applicationName": "Test Application",
                    "version": "1.0.0",
                    "entities": [
                        {
                            "name": "source",
                            "description": "source entity",
                            "table": "source_table",
                            "pathSegment": "source",
                            "linkName": "source",
                            "primaryKey":
                                {
                                    "name": "id",
                                    "description": "Unique identifier for the source entity",
                                    "type": "simple",
                                    "dataType": "uuid",
                                    "columnName": "source_id",
                                    "flags": [
                                        "readOnly"
                                    ],
                                    "constraints": [
                                        {
                                            "type": "required"
                                        },
                                        {
                                            "type": "unique"
                                        }
                                    ]
                                }
                        },
                        {
                            "name": "target",
                            "description": "target entity",
                            "table": "target_table",
                            "pathSegment": "target",
                            "linkName": "target",
                            "primaryKey":
                                {
                                    "name": "id",
                                    "description": "Unique identifier for the source entity",
                                    "type": "simple",
                                    "dataType": "uuid",
                                    "columnName": "source_id",
                                    "flags": [
                                        "readOnly"
                                    ],
                                    "constraints": [
                                        {
                                            "type": "required"
                                        },
                                        {
                                            "type": "unique"
                                        }
                                    ]
                                }
                        }
                    ],
                    "relations": [
                        {
                            "type": "one-to-one",
                            "sourceEndpoint":
                                {
                                    "entityName": "target",
                                    "name": "source",
                                    "pathSegment": "source",
                                    "linkName": "source",
                                    "required": false
                                },
                            "targetEndpoint":
                                {
                                    "entityName": "source",
                                    "required": false,
                                    "flags": ["hidden"]
                                },
                            "targetReference":"source_ref"
                        }
                    ]
                }
                """).allowingAnyArrayOrdering());
    }

    @Test
    void testManyToOneSerialization() {
        var sourceEntity = getEntity("source", "source entity", "source_table");

        var targetEntity = getEntity("target", "target entity", "target_table");

        var relation = ManyToOneRelation.builder()
                .sourceEndPoint(Relation.RelationEndPoint.builder()
                        .entity(sourceEntity.getName())
                        .build())
                .targetEndPoint(Relation.RelationEndPoint.builder()
                        .entity(targetEntity.getName())
                        .name(RelationName.of("source"))
                        .pathSegment(PathSegmentName.of("source"))
                        .linkName(LinkName.of("source"))
                        .build())
                .targetReference(ColumnName.of("target_ref"))
                .build();

        var app = Application.builder()
                .name(ApplicationName.of("Test Application"))
                .entities(List.of(sourceEntity, targetEntity))
                .relations(List.of(relation))
                .build();

        var converter = new DefaultApplicationSchemaConverter();

        var out = new ByteArrayOutputStream();
        converter.toJson(app, out);
        var json = out.toString(StandardCharsets.UTF_8);

        assertThat(json, sameJSONAs("""
                {
                    "$schema": "https://contentgrid.com/schemas/application-schema.json",
                    "applicationName": "Test Application",
                    "version": "1.0.0",
                    "entities": [
                        {
                            "name": "source",
                            "description": "source entity",
                            "table": "source_table",
                            "pathSegment": "source",
                            "linkName": "source",
                            "primaryKey":
                                {
                                    "name": "id",
                                    "description": "Unique identifier for the source entity",
                                    "type": "simple",
                                    "dataType": "uuid",
                                    "columnName": "source_id",
                                    "flags": [
                                        "readOnly"
                                    ],
                                    "constraints": [
                                        {
                                            "type": "required"
                                        },
                                        {
                                            "type": "unique"
                                        }
                                    ]
                                }
                        },
                        {
                            "name": "target",
                            "description": "target entity",
                            "table": "target_table",
                            "pathSegment": "target",
                            "linkName": "target",
                            "primaryKey":
                                {
                                    "name": "id",
                                    "description": "Unique identifier for the source entity",
                                    "type": "simple",
                                    "dataType": "uuid",
                                    "columnName": "source_id",
                                    "flags": [
                                        "readOnly"
                                    ],
                                    "constraints": [
                                        {
                                            "type": "required"
                                        },
                                        {
                                            "type": "unique"
                                        }
                                    ]
                                }
                        }
                    ],
                    "relations": [
                        {
                            "type": "one-to-many",
                            "sourceEndpoint":
                                {
                                    "entityName": "target",
                                    "name": "source",
                                    "pathSegment": "source",
                                    "linkName": "source",
                                    "required": false
                                },
                            "targetEndpoint":
                                {
                                    "entityName": "source",
                                    "required": false,
                                    "flags": [ "hidden" ]
                                },
                            "sourceReference":"target_ref"
                        }
                    ]
                }
                """).allowingAnyArrayOrdering());
    }

    private static Entity getEntity(String name, String description, String table) {
        return Entity.builder()
                .name(EntityName.of(name))
                .description(description)
                .table(TableName.of(table))
                .pathSegment(PathSegmentName.of(name))
                .linkName(LinkName.of(name))
                .primaryKey(
                        getPrimaryKey()
                )
                .build();
    }

    private static SimpleAttribute getPrimaryKey() {
        return SimpleAttribute.builder()
                .name(AttributeName.of("id"))
                .description("Unique identifier for the source entity")
                .type(Type.UUID)
                .column(ColumnName.of("source_id"))
                .flag(ReadOnlyFlag.INSTANCE)
                .constraint(Constraint.required())
                .constraint(Constraint.unique())
                .build();
    }
}
