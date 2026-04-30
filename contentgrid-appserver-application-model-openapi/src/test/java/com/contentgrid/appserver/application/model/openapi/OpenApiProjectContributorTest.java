package com.contentgrid.appserver.application.model.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiOperation;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter.In;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.HttpMethod;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.OpenApiPathItem;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiSpec;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaEnum;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaNull;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaObject;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaOneOf;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.relations.flags.RequiredEndpointFlag;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter.Operation;
import com.contentgrid.appserver.application.model.sortable.SortableField;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OpenApiProjectContributorTest {

    private static final YAMLMapper YAML_MAPPER = (YAMLMapper) new YAMLMapper()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);

    private static OpenApiOperation path(OpenApiSpec spec, String method, String path) {
        var item = spec.getPaths().getItems().get(path);
        assertThat(item).as("path '%s' should exist", path)
                .isNotNull().isInstanceOf(OpenApiPathItem.class);
        var op = ((OpenApiPathItem) item).getOperations().get(HttpMethod.valueOf(method.toUpperCase()));
        assertThat(op).as("%s %s should exist", method, path).isNotNull();
        return op;
    }

    private static List<OpenApiParameter> queryParameters(OpenApiOperation operation) {
        return operation.getParameters().stream()
                .map(OpenApiPotentialReference::getOriginalObject)
                .filter(p -> p.getIn() == In.QUERY)
                .toList();
    }

    // region demo model (party + insurance-case) -----------------------------------------------

    private static Application demoApplication() {
        var partyEntity = Entity.builder()
                .name(EntityName.of("party"))
                .pathSegment(PathSegmentName.of("parties"))
                .linkName(LinkName.of("parties"))
                .table(TableName.of("party"))
                .description("An individual or organization")
                .translationsBy(Locale.ROOT, t -> t.withSingularName("party"))
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("vat")).column(ColumnName.of("vat"))
                        .type(Type.TEXT).description("Vat number")
                        .constraint(Constraint.required())
                        .constraint(Constraint.unique())
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("name")).column(ColumnName.of("name"))
                        .type(Type.TEXT).description("Name of the party")
                        .constraint(Constraint.required())
                        .build())
                .attribute(ContentAttribute.builder()
                        .name(AttributeName.of("summary"))
                        .pathSegment(PathSegmentName.of("summary"))
                        .linkName(LinkName.of("summary"))
                        .idColumn(ColumnName.of("summary_id"))
                        .lengthColumn(ColumnName.of("summary_length"))
                        .mimetypeColumn(ColumnName.of("summary_mimetype"))
                        .filenameColumn(ColumnName.of("summary_filename"))
                        .description("Pdf file containing a summary of the party")
                        .build())
                .searchFilter(AttributeSearchFilter.builder()
                        .name(FilterName.of("name"))
                        .attributePath(PropertyPath.of(AttributeName.of("name")))
                        .operation(Operation.EXACT)
                        .translationsBy(Locale.ROOT, t -> t.withDescription("Name of the party"))
                        .build())
                .searchFilter(AttributeSearchFilter.builder()
                        .name(FilterName.of("name~prefix"))
                        .attributePath(PropertyPath.of(AttributeName.of("name")))
                        .operation(Operation.PREFIX)
                        .translationsBy(Locale.ROOT, t -> t.withDescription("Starts with name"))
                        .build())
                .searchFilter(AttributeSearchFilter.builder()
                        .name(FilterName.of("subsidiary.name"))
                        .attributePath(PropertyPath.of(RelationName.of("subsidiary"), AttributeName.of("name")))
                        .operation(Operation.EXACT)
                        .translationsBy(Locale.ROOT, t -> t.withDescription("Subsidiary: Name of the party"))
                        .build())
                .searchFilter(AttributeSearchFilter.builder()
                        .name(FilterName.of("subsidiary.name~prefix"))
                        .attributePath(PropertyPath.of(RelationName.of("subsidiary"), AttributeName.of("name")))
                        .operation(Operation.PREFIX)
                        .translationsBy(Locale.ROOT, t -> t.withDescription("Subsidiary: Starts with name"))
                        .build())
                .sortableField(SortableField.builder()
                        .name(SortableName.of("name"))
                        .propertyPath(PropertyPath.of(AttributeName.of("name")))
                        .build())
                .build();

        var insuranceCaseEntity = Entity.builder()
                .name(EntityName.of("insurance-case"))
                .pathSegment(PathSegmentName.of("insurance-cases"))
                .linkName(LinkName.of("insurance-cases"))
                .table(TableName.of("insurance_case"))
                .description("")
                .translationsBy(Locale.ROOT, t -> t.withSingularName("insurance-case"))
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("case_number")).column(ColumnName.of("case_number"))
                        .type(Type.LONG)
                        .constraint(Constraint.required())
                        .constraint(Constraint.unique())
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("created")).column(ColumnName.of("created"))
                        .type(Type.DATETIME)
                        .build())
                .attribute(ContentAttribute.builder()
                        .name(AttributeName.of("pdf"))
                        .pathSegment(PathSegmentName.of("pdf"))
                        .linkName(LinkName.of("pdf"))
                        .idColumn(ColumnName.of("pdf_id"))
                        .lengthColumn(ColumnName.of("pdf_length"))
                        .mimetypeColumn(ColumnName.of("pdf_mimetype"))
                        .filenameColumn(ColumnName.of("pdf_filename"))
                        .build())
                .attribute(ContentAttribute.builder()
                        .name(AttributeName.of("thumb_nail"))
                        .pathSegment(PathSegmentName.of("thumb-nail"))
                        .linkName(LinkName.of("thumb_nail"))
                        .idColumn(ColumnName.of("thumb_nail_id"))
                        .lengthColumn(ColumnName.of("thumb_nail_length"))
                        .mimetypeColumn(ColumnName.of("thumb_nail_mimetype"))
                        .filenameColumn(ColumnName.of("thumb_nail_filename"))
                        .build())
                .searchFilter(AttributeSearchFilter.builder()
                        .name(FilterName.of("owning_party.name"))
                        .attributePath(PropertyPath.of(RelationName.of("owning_party"), AttributeName.of("name")))
                        .operation(Operation.EXACT)
                        .translationsBy(Locale.ROOT, t -> t.withDescription("Owning party: Name of the party"))
                        .build())
                .searchFilter(AttributeSearchFilter.builder()
                        .name(FilterName.of("owning_party.name~prefix"))
                        .attributePath(PropertyPath.of(RelationName.of("owning_party"), AttributeName.of("name")))
                        .operation(Operation.PREFIX)
                        .translationsBy(Locale.ROOT, t -> t.withDescription("Owning party: Starts with name"))
                        .build())
                .build();

        return Application.builder()
                .name(ApplicationName.of("DemoApplication"))
                .entity(partyEntity)
                .entity(insuranceCaseEntity)
                .relation(ManyToOneRelation.builder()
                        .sourceEndPoint(RelationEndPoint.builder()
                                .entity(EntityName.of("party"))
                                .name(RelationName.of("subsidiary"))
                                .pathSegment(PathSegmentName.of("subsidiary"))
                                .linkName(LinkName.of("subsidiary"))
                                .description("Subsidiary of the party")
                                .build())
                        .targetEndPoint(RelationEndPoint.builder()
                                .entity(EntityName.of("party"))
                                .build())
                        .targetReference(ColumnName.of("subsidiary_id"))
                        .build())
                .relation(ManyToOneRelation.builder()
                        .sourceEndPoint(RelationEndPoint.builder()
                                .entity(EntityName.of("insurance-case"))
                                .name(RelationName.of("owning_party"))
                                .pathSegment(PathSegmentName.of("owning-party"))
                                .linkName(LinkName.of("owning_party"))
                                .flag(RequiredEndpointFlag.INSTANCE)
                                .build())
                        .targetEndPoint(RelationEndPoint.builder()
                                .entity(EntityName.of("party"))
                                .build())
                        .targetReference(ColumnName.of("owning_party_id"))
                        .build())
                .relation(OneToManyRelation.builder()
                        .sourceEndPoint(RelationEndPoint.builder()
                                .entity(EntityName.of("insurance-case"))
                                .name(RelationName.of("followups"))
                                .pathSegment(PathSegmentName.of("followups"))
                                .linkName(LinkName.of("followups"))
                                .build())
                        .targetEndPoint(RelationEndPoint.builder()
                                .entity(EntityName.of("insurance-case"))
                                .name(RelationName.of("parent_case"))
                                .flag(HiddenEndpointFlag.INSTANCE)
                                .build())
                        .sourceReference(ColumnName.of("parent_case_id"))
                        .build())
                .build();
    }

    // endregion

    @Test
    void openApiSpec_isContributed() throws Exception {
        var spec = OpenApiSpecBuilder.convert(demoApplication());

        var yaml = YAML_MAPPER.writeValueAsString(spec);

        String expectedOpenApi;
        try (var is = getClass().getResourceAsStream("openApiSpec_isContributed.yaml")) {
            expectedOpenApi = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(yaml).isEqualTo(expectedOpenApi);
    }

    @Test
    void openApiSpec_biDirectionalRelation() {
        var spec = OpenApiSpecBuilder.convert(Application.builder()
                .name(ApplicationName.of("test-app"))
                .entity(Entity.builder()
                        .name(EntityName.of("invoice"))
                        .pathSegment(PathSegmentName.of("invoices"))
                        .linkName(LinkName.of("invoices"))
                        .table(TableName.of("invoice"))
                        .build())
                .entity(Entity.builder()
                        .name(EntityName.of("supplier"))
                        .pathSegment(PathSegmentName.of("suppliers"))
                        .linkName(LinkName.of("suppliers"))
                        .table(TableName.of("supplier"))
                        .build())
                .relation(ManyToOneRelation.builder()
                        .sourceEndPoint(RelationEndPoint.builder()
                                .entity(EntityName.of("invoice"))
                                .name(RelationName.of("supplier"))
                                .pathSegment(PathSegmentName.of("supplier"))
                                .linkName(LinkName.of("supplier"))
                                .build())
                        .targetEndPoint(RelationEndPoint.builder()
                                .entity(EntityName.of("supplier"))
                                .name(RelationName.of("invoices"))
                                .pathSegment(PathSegmentName.of("invoices"))
                                .linkName(LinkName.of("invoices"))
                                .build())
                        .targetReference(ColumnName.of("supplier_id"))
                        .build())
                .build());

        assertThat(path(spec, "get", "/invoices/{id}/supplier")).isNotNull();
        assertThat(path(spec, "get", "/suppliers/{id}/invoices")).isNotNull();
    }

    private static AttributeSearchFilter exactFilter(String attributeName) {
        return AttributeSearchFilter.builder()
                .name(FilterName.of(attributeName))
                .attributePath(PropertyPath.of(AttributeName.of(attributeName)))
                .operation(Operation.EXACT)
                .build();
    }

    @Test
    void openApiSpec_withEnumType() {
        var spec = OpenApiSpecBuilder.convert(Application.builder()
                .name(ApplicationName.of("test-app"))
                .entity(Entity.builder()
                        .name(EntityName.of("party"))
                        .pathSegment(PathSegmentName.of("parties"))
                        .linkName(LinkName.of("parties"))
                        .table(TableName.of("party"))
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("name")).column(ColumnName.of("name"))
                                .type(Type.TEXT).constraint(Constraint.required()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("gender")).column(ColumnName.of("gender"))
                                .type(Type.TEXT)
                                .constraint(Constraint.allowedValues(List.of("female", "male")))
                                .build())
                        .searchFilter(exactFilter("name"))
                        .searchFilter(exactFilter("gender"))
                        .build())
                .build());

        var collectionParams = queryParameters(path(spec, "get", "/parties"));

        assertThat(collectionParams).anySatisfy(collectionParam -> {
            assertThat(collectionParam.getName()).isEqualTo("gender");
            assertThat(collectionParam.getSchema()).isInstanceOfSatisfying(JsonSchemaEnum.class,
                    schema -> assertThat(schema.getEnum()).containsExactly("female", "male"));
        });

        var plainSchemas = List.of("partyResponse", "partyPostBody", "partyPutBody",  "partyPatchBody");
        var formSchemas = List.of("partyPostFormBody", "partyPostMultipartFormDataBody", "partyPutFormBody", "partyPatchFormBody");

        assertThat(plainSchemas).allSatisfy(schema -> {
            assertThat(spec.getComponents().getSchemas().getItem(schema))
                    .isInstanceOfSatisfying(JsonSchemaObject.class, object -> {
                        assertThat(object.getProperties()).containsKey("gender")
                                .extracting("gender")
                                .isEqualTo(new JsonSchemaEnum(List.of("female", "male")).orNull());
                    });
        });
        assertThat(formSchemas).allSatisfy(schema -> {
            assertThat(spec.getComponents().getSchemas().getItem(schema))
                    .isInstanceOfSatisfying(JsonSchemaObject.class, object -> {
                        assertThat(object.getProperties()).containsKey("gender")
                                .extracting("gender")
                                .isEqualTo(new JsonSchemaEnum(List.of("female", "male")));
                        assertThat(object.getRequired()).doesNotContain("gender");
                    });
        });
    }

}
