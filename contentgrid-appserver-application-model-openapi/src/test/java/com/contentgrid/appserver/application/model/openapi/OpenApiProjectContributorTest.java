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
import com.contentgrid.appserver.application.model.openapi.model.OpenApiSpec;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaArray;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaEnum;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaObject;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.relations.flags.RequiredEndpointFlag;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter.Operation;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code com.contentgrid.scribe.generator.openapi.OpenApiProjectContributorTest} from
 * {@code contentcloud-scribe}.
 *
 * <p>The scaffolding has been rewritten to drive {@link OpenApiSpecBuilder} with
 * {@link Application} values instead of scribe's changeset model, but the expected YAML output
 * in {@link #openApiSpec_isContributed()} is kept byte-identical to the scribe test so that any
 * divergence is visible in the diff.
 */
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
                        .translationsBy(Locale.ROOT, t -> t.withDescription("Name of the party"))
                        .build())
                .searchFilter(AttributeSearchFilter.builder()
                        .name(FilterName.of("subsidiary.name~prefix"))
                        .attributePath(PropertyPath.of(RelationName.of("subsidiary"), AttributeName.of("name")))
                        .operation(Operation.PREFIX)
                        .translationsBy(Locale.ROOT, t -> t.withDescription("Starts with name"))
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
                        .translationsBy(Locale.ROOT, t -> t.withDescription("Name of the party"))
                        .build())
                .searchFilter(AttributeSearchFilter.builder()
                        .name(FilterName.of("owning_party.name~prefix"))
                        .attributePath(PropertyPath.of(RelationName.of("owning_party"), AttributeName.of("name")))
                        .operation(Operation.PREFIX)
                        .translationsBy(Locale.ROOT, t -> t.withDescription("Starts with name"))
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

    @Test
    void openApiSpec_issue_ACC780() {
        var spec = OpenApiSpecBuilder.convert(Application.builder()
                .name(ApplicationName.of("test-app"))
                .entity(Entity.builder()
                        .name(EntityName.of("invoice"))
                        .pathSegment(PathSegmentName.of("invoices"))
                        .linkName(LinkName.of("invoices"))
                        .table(TableName.of("invoice"))
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("received")).column(ColumnName.of("received"))
                                .type(Type.DATETIME).constraint(Constraint.required()).build())
                        .attribute(ContentAttribute.builder()
                                .name(AttributeName.of("document"))
                                .pathSegment(PathSegmentName.of("document"))
                                .linkName(LinkName.of("document"))
                                .idColumn(ColumnName.of("document_id"))
                                .lengthColumn(ColumnName.of("document_length"))
                                .mimetypeColumn(ColumnName.of("document_mimetype"))
                                .filenameColumn(ColumnName.of("document_filename"))
                                .build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("pay_before")).column(ColumnName.of("pay_before"))
                                .type(Type.DATETIME).constraint(Constraint.required()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("total_amount")).column(ColumnName.of("total_amount"))
                                .type(Type.DOUBLE).constraint(Constraint.required()).build())
                        .searchFilter(AttributeSearchFilter.builder()
                                .name(FilterName.of("pay_before"))
                                .attributePath(PropertyPath.of(AttributeName.of("pay_before")))
                                .operation(Operation.EXACT).build())
                        .searchFilter(AttributeSearchFilter.builder()
                                .name(FilterName.of("pay_before~before"))
                                .attributePath(PropertyPath.of(AttributeName.of("pay_before")))
                                .operation(Operation.LESS_THAN)
                                .translationsBy(Locale.ROOT, t -> t.withDescription("Before pay_before"))
                                .build())
                        .searchFilter(AttributeSearchFilter.builder()
                                .name(FilterName.of("pay_before~after"))
                                .attributePath(PropertyPath.of(AttributeName.of("pay_before")))
                                .operation(Operation.GREATER_THAN).build())
                        .searchFilter(AttributeSearchFilter.builder()
                                .name(FilterName.of("supplier.name"))
                                .attributePath(PropertyPath.of(RelationName.of("supplier"), AttributeName.of("name")))
                                .operation(Operation.EXACT).build())
                        .searchFilter(AttributeSearchFilter.builder()
                                .name(FilterName.of("supplier.bank_account"))
                                .attributePath(PropertyPath.of(RelationName.of("supplier"), AttributeName.of("bank_account")))
                                .operation(Operation.EXACT).build())
                        .build())
                .entity(Entity.builder()
                        .name(EntityName.of("supplier"))
                        .pathSegment(PathSegmentName.of("suppliers"))
                        .linkName(LinkName.of("suppliers"))
                        .table(TableName.of("supplier"))
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("name")).column(ColumnName.of("name"))
                                .type(Type.TEXT).constraint(Constraint.required()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("telephone")).column(ColumnName.of("telephone"))
                                .type(Type.TEXT).constraint(Constraint.required()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("bank_account")).column(ColumnName.of("bank_account"))
                                .type(Type.TEXT).constraint(Constraint.unique()).build())
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
                                .build())
                        .targetReference(ColumnName.of("supplier_id"))
                        .build())
                .build());

        assertThat(queryParameters(path(spec, "get", "/invoices")))
                .map(OpenApiParameter::getName)
                .containsExactlyInAnyOrder(
                        "pay_before",
                        "pay_before~before",
                        "pay_before~after",
                        "supplier.name",
                        "supplier.bank_account",
                        "_cursor", "_size"
                );
        assertThat(queryParameters(path(spec, "get", "/invoices")).stream()
                .filter(parameter -> "pay_before~before".equals(parameter.getName())))
                .map(OpenApiParameter::getDescription)
                .containsExactly("Before pay_before");
    }

    @Test
    void openApiSpec_extraOperators() {
        var spec = OpenApiSpecBuilder.convert(Application.builder()
                .name(ApplicationName.of("test-app"))
                .entity(Entity.builder()
                        .name(EntityName.of("invoice"))
                        .pathSegment(PathSegmentName.of("invoices"))
                        .linkName(LinkName.of("invoices"))
                        .table(TableName.of("invoice"))
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("pay_before")).column(ColumnName.of("pay_before"))
                                .type(Type.DATETIME).constraint(Constraint.required()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("total_amount")).column(ColumnName.of("total_amount"))
                                .type(Type.DOUBLE).constraint(Constraint.required()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("item_count")).column(ColumnName.of("item_count"))
                                .type(Type.LONG).constraint(Constraint.required()).build())
                        .searchFilter(exactFilter("pay_before")).searchFilter(rangeFilter("pay_before", "before", Operation.LESS_THAN)).searchFilter(rangeFilter("pay_before", "after", Operation.GREATER_THAN))
                        .searchFilter(exactFilter("total_amount"))
                        .searchFilter(rangeFilter("total_amount", "lt", Operation.LESS_THAN))
                        .searchFilter(rangeFilter("total_amount", "lte", Operation.LESS_THAN_OR_EQUAL))
                        .searchFilter(rangeFilter("total_amount", "gt", Operation.GREATER_THAN))
                        .searchFilter(rangeFilter("total_amount", "gte", Operation.GREATER_THAN_OR_EQUAL))
                        .searchFilter(exactFilter("item_count"))
                        .searchFilter(rangeFilter("item_count", "lt", Operation.LESS_THAN))
                        .searchFilter(rangeFilter("item_count", "lte", Operation.LESS_THAN_OR_EQUAL))
                        .searchFilter(rangeFilter("item_count", "gt", Operation.GREATER_THAN))
                        .searchFilter(rangeFilter("item_count", "gte", Operation.GREATER_THAN_OR_EQUAL))
                        .build())
                .build());

        assertThat(queryParameters(path(spec, "get", "/invoices")))
                .map(OpenApiParameter::getName)
                .containsExactlyInAnyOrder(
                        "pay_before",
                        "pay_before~before",
                        "pay_before~after",
                        "total_amount",
                        "total_amount~lt",
                        "total_amount~lte",
                        "total_amount~gt",
                        "total_amount~gte",
                        "item_count",
                        "item_count~lt",
                        "item_count~lte",
                        "item_count~gt",
                        "item_count~gte",
                        "_cursor", "_size"
                );
    }

    private static AttributeSearchFilter exactFilter(String attributeName) {
        return AttributeSearchFilter.builder()
                .name(FilterName.of(attributeName))
                .attributePath(PropertyPath.of(AttributeName.of(attributeName)))
                .operation(Operation.EXACT)
                .build();
    }

    private static AttributeSearchFilter rangeFilter(String attributeName, String operatorSuffix, Operation operation) {
        return AttributeSearchFilter.builder()
                .name(FilterName.of(attributeName + "~" + operatorSuffix))
                .attributePath(PropertyPath.of(AttributeName.of(attributeName)))
                .operation(operation)
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

        var schemas = List.of("partyResponse", "partyPostBody", "partyPutBody", "partyPatchBody");

        assertThat(schemas).allSatisfy(schema -> {
            assertThat(spec.getComponents().getSchemas().getItem(schema))
                    .isInstanceOfSatisfying(JsonSchemaObject.class, object -> {
                        assertThat(object.getProperties()).containsKey("gender");
                    });
        });
    }

    @Test
    void openApiSpec_withTextSearch() {
        var spec = OpenApiSpecBuilder.convert(Application.builder()
                .name(ApplicationName.of("test-app"))
                .entity(Entity.builder()
                        .name(EntityName.of("party"))
                        .pathSegment(PathSegmentName.of("parties"))
                        .linkName(LinkName.of("parties"))
                        .table(TableName.of("party"))
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("exact_and_prefix")).column(ColumnName.of("exact_and_prefix"))
                                .type(Type.TEXT).constraint(Constraint.required()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("exact_only")).column(ColumnName.of("exact_only"))
                                .type(Type.TEXT).constraint(Constraint.required()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("prefix_only")).column(ColumnName.of("prefix_only"))
                                .type(Type.TEXT).constraint(Constraint.required()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("no_search")).column(ColumnName.of("no_search"))
                                .type(Type.TEXT).build())
                        .searchFilter(exactFilter("exact_and_prefix"))
                        .searchFilter(AttributeSearchFilter.builder()
                                .name(FilterName.of("exact_and_prefix~prefix"))
                                .attributePath(PropertyPath.of(AttributeName.of("exact_and_prefix")))
                                .operation(Operation.PREFIX).build())
                        .searchFilter(exactFilter("exact_only"))
                        .searchFilter(AttributeSearchFilter.builder()
                                .name(FilterName.of("prefix_only~prefix"))
                                .attributePath(PropertyPath.of(AttributeName.of("prefix_only")))
                                .operation(Operation.PREFIX).build())
                        .build())
                .build());

        assertThat(queryParameters(path(spec, "get", "/parties")))
                .map(OpenApiParameter::getName)
                .contains("exact_and_prefix", "exact_and_prefix~prefix", "exact_only", "prefix_only~prefix")
                .doesNotContain("exact_only~prefix", "prefix_only", "no_search", "no_search~prefix");
    }

    @Test
    void withCuriedLinkRelations() {
        var spec = OpenApiSpecBuilder.convert(Application.builder()
                .name(ApplicationName.of("test-app"))
                .entity(Entity.builder()
                        .name(EntityName.of("party"))
                        .pathSegment(PathSegmentName.of("parties"))
                        .linkName(LinkName.of("parties"))
                        .table(TableName.of("party"))
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("vat")).column(ColumnName.of("vat"))
                                .type(Type.TEXT)
                                .constraint(Constraint.required()).constraint(Constraint.unique()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("name")).column(ColumnName.of("name"))
                                .type(Type.TEXT).constraint(Constraint.required()).build())
                        .attribute(ContentAttribute.builder()
                                .name(AttributeName.of("summary"))
                                .pathSegment(PathSegmentName.of("summary"))
                                .linkName(LinkName.of("summary"))
                                .idColumn(ColumnName.of("summary_id"))
                                .lengthColumn(ColumnName.of("summary_length"))
                                .mimetypeColumn(ColumnName.of("summary_mimetype"))
                                .filenameColumn(ColumnName.of("summary_filename"))
                                .build())
                        .searchFilter(exactFilter("name"))
                        .build())
                .build());

        var collectionSchema = spec.getComponents().getSchemas().getItem("partyCollection");

        assertThat(collectionSchema).isInstanceOfSatisfying(JsonSchemaObject.class, collection ->
                assertThat(collection.getProperties().get("_embedded")).isInstanceOfSatisfying(JsonSchemaObject.class,
                        embedded -> assertThat(embedded.getProperties()).containsKey("item")));
    }

    @Test
    void dateRangeSearch() {
        var spec = OpenApiSpecBuilder.convert(Application.builder()
                .name(ApplicationName.of("test-app"))
                .entity(Entity.builder()
                        .name(EntityName.of("party"))
                        .pathSegment(PathSegmentName.of("parties"))
                        .linkName(LinkName.of("parties"))
                        .table(TableName.of("party"))
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("date")).column(ColumnName.of("date"))
                                .type(Type.DATETIME).constraint(Constraint.required()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("date_unindexed")).column(ColumnName.of("date_unindexed"))
                                .type(Type.DATETIME).build())
                        .searchFilter(exactFilter("date"))
                        .searchFilter(rangeFilter("date", "before", Operation.LESS_THAN))
                        .searchFilter(rangeFilter("date", "after", Operation.GREATER_THAN))
                        .build())
                .build());

        assertThat(queryParameters(path(spec, "get", "/parties")))
                .map(OpenApiParameter::getName)
                .contains("date", "date~before", "date~after")
                .doesNotContain("date_unindexed", "date_unindexed~before", "date_unindexed~after");
    }

    @Test
    void cursorBasedPagination() {
        var spec = OpenApiSpecBuilder.convert(Application.builder()
                .name(ApplicationName.of("test-app"))
                .entity(Entity.builder()
                        .name(EntityName.of("party"))
                        .pathSegment(PathSegmentName.of("parties"))
                        .linkName(LinkName.of("parties"))
                        .table(TableName.of("party"))
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("vat")).column(ColumnName.of("vat"))
                                .type(Type.TEXT)
                                .constraint(Constraint.required()).constraint(Constraint.unique()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("name")).column(ColumnName.of("name"))
                                .type(Type.TEXT).constraint(Constraint.required()).build())
                        .searchFilter(exactFilter("name"))
                        .build())
                .build());

        assertThat(queryParameters(path(spec, "get", "/parties")))
                .map(OpenApiParameter::getName)
                .contains("_cursor", "_size")
                .doesNotContain("page", "size", "sort");
    }

    @Test
    void omitLegacyPageMetadata() {
        var spec = OpenApiSpecBuilder.convert(Application.builder()
                .name(ApplicationName.of("test-app"))
                .entity(Entity.builder()
                        .name(EntityName.of("party"))
                        .pathSegment(PathSegmentName.of("parties"))
                        .linkName(LinkName.of("parties"))
                        .table(TableName.of("party"))
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("vat")).column(ColumnName.of("vat"))
                                .type(Type.TEXT)
                                .constraint(Constraint.required()).constraint(Constraint.unique()).build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("name")).column(ColumnName.of("name"))
                                .type(Type.TEXT).constraint(Constraint.required()).build())
                        .searchFilter(exactFilter("name"))
                        .build())
                .build());

        assertThat(spec.getComponents().getSchemas().getItems()).hasEntrySatisfying("page", page ->
                assertThat(page).isInstanceOfSatisfying(JsonSchemaObject.class, pageObject -> {
                    assertThat(pageObject.getProperties()).doesNotContainKeys("totalPages", "totalElements");
                    assertThat(pageObject.getProperties())
                            .containsKeys(pageObject.getRequired().toArray(String[]::new));
                }));
    }
}
