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

        // @formatter:off
        var expectedOpenApi =
                """
                        openapi: "3.0.2"
                        info:
                          version: "0.0.1-SNAPSHOT"
                          title: "DemoApplication"
                        tags:
                        - name: "party"
                          description: "An individual or organization"
                        - name: "insurance-case"
                          description: ""
                        paths:
                          /parties:
                            get:
                              tags:
                              - "party"
                              operationId: "get-parties"
                              summary: "Retrieve party list"
                              parameters:
                              - name: "name"
                                in: "query"
                                description: "Name of the party"
                                required: false
                                schema:
                                  type: "string"
                              - name: "name~prefix"
                                in: "query"
                                description: "Starts with name"
                                required: false
                                schema:
                                  type: "string"
                              - name: "subsidiary.name"
                                in: "query"
                                description: "Name of the party"
                                required: false
                                schema:
                                  type: "string"
                              - name: "subsidiary.name~prefix"
                                in: "query"
                                description: "Starts with name"
                                required: false
                                schema:
                                  type: "string"
                              - name: "_cursor"
                                in: "query"
                                description: "Cursor to access a page (cursors are server-generated and supplied\\
                                  \\ in the page metadata)"
                                required: false
                                schema:
                                  type: "string"
                                  example: "1mlpulv1"
                              - name: "_size"
                                in: "query"
                                description: "Page size"
                                required: false
                                schema:
                                  type: "integer"
                              - name: "_sort"
                                in: "query"
                                required: false
                                schema:
                                  type: "array"
                                  items:
                                    type: "string"
                                    enum:
                                    - "name,asc"
                                    - "name,desc"
                                    example: "name,asc"
                              responses:
                                "200":
                                  description: "OK"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/partyCollection"
                            post:
                              tags:
                              - "party"
                              operationId: "create-party"
                              summary: "Create a new party"
                              requestBody:
                                required: true
                                content:
                                  application/json:
                                    schema:
                                      $ref: "#/components/schemas/partyPostBody"
                                  multipart/form-data:
                                    schema:
                                      $ref: "#/components/schemas/partyPostMultipartFormDataBody"
                              responses:
                                "201":
                                  description: "The party has been created"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/partyResponse"
                                "400":
                                  description: "The party can not be created due to a problem with the submitted\\
                                    \\ data"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                                "409":
                                  description: "The party can not be created due to a unique attribute already\\
                                    \\ existing with that value"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                          /parties/{id}:
                            get:
                              tags:
                              - "party"
                              operationId: "get-party"
                              summary: "Retrieve the party"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "200":
                                  description: "OK"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/partyResponse"
                            put:
                              tags:
                              - "party"
                              operationId: "update-party"
                              summary: "Update all attributes of the party"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                required: true
                                content:
                                  application/json:
                                    schema:
                                      $ref: "#/components/schemas/partyPutBody"
                              responses:
                                "200":
                                  description: "The party has been updated"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/partyResponse"
                                "400":
                                  description: "The party can not be updated due to a problem with the submitted\\
                                    \\ data"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                                "409":
                                  description: "The party can not be updated due to a unique attribute already\\
                                    \\ existing with that value"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                            patch:
                              tags:
                              - "party"
                              operationId: "patch-party"
                              summary: "Update some attributes of the party"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                description: "Only attributes that have to be updated should be specified,\\
                                  \\ other attributes should not be present"
                                required: true
                                content:
                                  application/json:
                                    schema:
                                      $ref: "#/components/schemas/partyPatchBody"
                              responses:
                                "200":
                                  description: "The party has been updated"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/partyResponse"
                                "400":
                                  description: "The party can not be updated due to a problem with the submitted\\
                                    \\ data"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                                "409":
                                  description: "The party can not be updated due to a unique attribute already\\
                                    \\ existing with that value"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                            delete:
                              tags:
                              - "party"
                              operationId: "delete-party"
                              summary: "Delete the party"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "204":
                                  description: "The party has been deleted"
                                "409":
                                  description: "The party can not be deleted because it is linked via a required\\
                                    \\ relation"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                          /parties/{id}/subsidiary:
                            get:
                              tags:
                              - "party"
                              operationId: "get-party-subsidiary"
                              summary: "Retrieve the party linked with party as subsidiary"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "200":
                                  description: "OK"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/partyResponse"
                                "404":
                                  description: "The subsidiary relation does not link to any party"
                            put:
                              tags:
                              - "party"
                              operationId: "update-party-subsidiary"
                              summary: "Set the party that is linked with party as subsidiary"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                description: "One party URI"
                                required: true
                                content:
                                  text/uri-list:
                                    schema:
                                      type: "string"
                                      example: "/parties/00000000-0000-0000-0000-000000000000"
                              responses:
                                "204":
                                  description: "The party is linked with party as subsidiary"
                                "400":
                                  description: "Multiple URIs are given, but subsidiary can only refer to\\
                                    \\ one item"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                                "409":
                                  description: "The party is already linked with a different party as subsidiary"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                            delete:
                              tags:
                              - "party"
                              operationId: "delete-party-subsidiary"
                              summary: "Removes the link to party from subsidiary"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "204":
                                  description: "The link to party has been removed from subsidiary"
                                "400":
                                  description: "You can not remove the subsidiary link, because it is marked\\
                                    \\ as required"
                                "409":
                                  description: "You can not remove the subsidiary link, because the inverse\\
                                    \\ relation is marked as required"
                          /insurance-cases:
                            get:
                              tags:
                              - "insurance-case"
                              operationId: "get-insurance-cases"
                              summary: "Retrieve insurance-case list"
                              parameters:
                              - name: "owning_party.name"
                                in: "query"
                                description: "Name of the party"
                                required: false
                                schema:
                                  type: "string"
                              - name: "owning_party.name~prefix"
                                in: "query"
                                description: "Starts with name"
                                required: false
                                schema:
                                  type: "string"
                              - name: "_cursor"
                                in: "query"
                                description: "Cursor to access a page (cursors are server-generated and supplied\\
                                  \\ in the page metadata)"
                                required: false
                                schema:
                                  type: "string"
                                  example: "1mlpulv1"
                              - name: "_size"
                                in: "query"
                                description: "Page size"
                                required: false
                                schema:
                                  type: "integer"
                              responses:
                                "200":
                                  description: "OK"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/insurance-caseCollection"
                            post:
                              tags:
                              - "insurance-case"
                              operationId: "create-insurance-case"
                              summary: "Create a new insurance-case"
                              requestBody:
                                required: true
                                content:
                                  application/json:
                                    schema:
                                      $ref: "#/components/schemas/insurance-casePostBody"
                                  multipart/form-data:
                                    schema:
                                      $ref: "#/components/schemas/insurance-casePostMultipartFormDataBody"
                              responses:
                                "201":
                                  description: "The insurance-case has been created"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/insurance-caseResponse"
                                "400":
                                  description: "The insurance-case can not be created due to a problem with\\
                                    \\ the submitted data"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                                "409":
                                  description: "The insurance-case can not be created due to a unique attribute\\
                                    \\ already existing with that value"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                          /insurance-cases/{id}:
                            get:
                              tags:
                              - "insurance-case"
                              operationId: "get-insurance-case"
                              summary: "Retrieve the insurance-case"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "200":
                                  description: "OK"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/insurance-caseResponse"
                            put:
                              tags:
                              - "insurance-case"
                              operationId: "update-insurance-case"
                              summary: "Update all attributes of the insurance-case"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                required: true
                                content:
                                  application/json:
                                    schema:
                                      $ref: "#/components/schemas/insurance-casePutBody"
                              responses:
                                "200":
                                  description: "The insurance-case has been updated"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/insurance-caseResponse"
                                "400":
                                  description: "The insurance-case can not be updated due to a problem with\\
                                    \\ the submitted data"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                                "409":
                                  description: "The insurance-case can not be updated due to a unique attribute\\
                                    \\ already existing with that value"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                            patch:
                              tags:
                              - "insurance-case"
                              operationId: "patch-insurance-case"
                              summary: "Update some attributes of the insurance-case"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                description: "Only attributes that have to be updated should be specified,\\
                                  \\ other attributes should not be present"
                                required: true
                                content:
                                  application/json:
                                    schema:
                                      $ref: "#/components/schemas/insurance-casePatchBody"
                              responses:
                                "200":
                                  description: "The insurance-case has been updated"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/insurance-caseResponse"
                                "400":
                                  description: "The insurance-case can not be updated due to a problem with\\
                                    \\ the submitted data"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                                "409":
                                  description: "The insurance-case can not be updated due to a unique attribute\\
                                    \\ already existing with that value"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                            delete:
                              tags:
                              - "insurance-case"
                              operationId: "delete-insurance-case"
                              summary: "Delete the insurance-case"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "204":
                                  description: "The insurance-case has been deleted"
                                "409":
                                  description: "The insurance-case can not be deleted because it is linked\\
                                    \\ via a required relation"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                          /insurance-cases/{id}/owning-party:
                            get:
                              tags:
                              - "insurance-case"
                              operationId: "get-insurance-case-owning_party"
                              summary: "Retrieve the party linked with insurance-case as owning_party"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "200":
                                  description: "OK"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/partyResponse"
                                "404":
                                  description: "The owning_party relation does not link to any party"
                            put:
                              tags:
                              - "insurance-case"
                              operationId: "update-insurance-case-owning_party"
                              summary: "Set the party that is linked with insurance-case as owning_party"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                description: "One party URI"
                                required: true
                                content:
                                  text/uri-list:
                                    schema:
                                      type: "string"
                                      example: "/parties/00000000-0000-0000-0000-000000000000"
                              responses:
                                "204":
                                  description: "The party is linked with insurance-case as owning_party"
                                "400":
                                  description: "Multiple URIs are given, but owning_party can only refer to\\
                                    \\ one item"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                                "409":
                                  description: "The party is already linked with a different insurance-case\\
                                    \\ as owning_party"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                            delete:
                              tags:
                              - "insurance-case"
                              operationId: "delete-insurance-case-owning_party"
                              summary: "Removes the link to party from owning_party"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "204":
                                  description: "The link to party has been removed from owning_party"
                                "400":
                                  description: "You can not remove the owning_party link, because it is marked\\
                                    \\ as required"
                                "409":
                                  description: "You can not remove the owning_party link, because the inverse\\
                                    \\ relation is marked as required"
                          /insurance-cases/{id}/followups:
                            get:
                              tags:
                              - "insurance-case"
                              operationId: "get-insurance-case-followups"
                              summary: "Retrieve the insurance-case list linked with insurance-case as followups"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "200":
                                  description: "OK"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/insurance-caseCollection"
                            post:
                              tags:
                              - "insurance-case"
                              operationId: "add-insurance-case-followups"
                              summary: "Add links to insurance-case list that is linked with insurance-case\\
                                \\ as followups, in addition to existing insurance-case list"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                description: "Newline separated list of insurance-case URIs"
                                required: true
                                content:
                                  text/uri-list:
                                    schema:
                                      type: "string"
                                      example: "/insurance-cases/00000000-0000-0000-0000-000000000000"
                              responses:
                                "204":
                                  description: "The list of insurance-case is linked with insurance-case as\\
                                    \\ followups"
                                "409":
                                  description: "The URI list contains some insurance-case that is already\\
                                    \\ linked with a different insurance-case as followups"
                                  content:
                                    application/problem+json:
                                      schema:
                                        $ref: "#/components/schemas/problemDetailDiscriminator"
                            delete:
                              tags:
                              - "insurance-case"
                              operationId: "delete-insurance-case-followups"
                              summary: "Removes all links to insurance-case from followups"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "204":
                                  description: "All links to insurance-case have been removed from followups"
                                "409":
                                  description: "You can not remove the followups link, because the inverse\\
                                    \\ relation is marked as required"
                          /insurance-cases/{id}/followups/{itemId}:
                            get:
                              tags:
                              - "insurance-case"
                              operationId: "get-insurance-case-followups-item"
                              summary: "Retrieve the insurance-case identified by 'itemId' linked with insurance-case\\
                                \\ as followups"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              - name: "itemId"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "200":
                                  description: "OK"
                                  content:
                                    application/json:
                                      schema:
                                        $ref: "#/components/schemas/insurance-caseResponse"
                                "404":
                                  description: "The followups relation does not link to the insurance-case\\
                                    \\ identified by 'itemId'"
                            delete:
                              tags:
                              - "insurance-case"
                              operationId: "delete-insurance-case-followups-item"
                              summary: "Removes the link to insurance-case identified by 'itemId' from followups"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              - name: "itemId"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "204":
                                  description: "The link to insurance-case has been removed from followups"
                                "409":
                                  description: "You can not remove the followups link, because the inverse\\
                                    \\ relation is marked as required"
                          /parties/{id}/summary:
                            get:
                              tags:
                              - "party"
                              operationId: "get-party-summary"
                              summary: "Retrieve the summary file stored with party"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "200":
                                  description: "Contents of the stored file"
                                  content:
                                    '*/*':
                                      schema:
                                        type: "string"
                                        format: "binary"
                                "404":
                                  description: "No summary file is stored with party"
                            put:
                              tags:
                              - "party"
                              operationId: "put-party-summary"
                              summary: "Add or update the summary file stored with party"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                description: "File data to store"
                                required: true
                                content:
                                  '*/*':
                                    schema:
                                      type: "string"
                                      format: "binary"
                              responses:
                                "204":
                                  description: "The file is uploaded"
                            post:
                              tags:
                              - "party"
                              operationId: "post-party-summary"
                              summary: "Add or update the summary file stored with party"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                description: "File data to store"
                                required: true
                                content:
                                  '*/*':
                                    schema:
                                      type: "string"
                                      format: "binary"
                              responses:
                                "204":
                                  description: "The file is uploaded"
                            delete:
                              tags:
                              - "party"
                              operationId: "delete-party-summary"
                              summary: "Delete the summary file stored with party"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "204":
                                  description: "The file has been deleted"
                          /insurance-cases/{id}/pdf:
                            get:
                              tags:
                              - "insurance-case"
                              operationId: "get-insurance-case-pdf"
                              summary: "Retrieve the pdf file stored with insurance-case"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "200":
                                  description: "Contents of the stored file"
                                  content:
                                    '*/*':
                                      schema:
                                        type: "string"
                                        format: "binary"
                                "404":
                                  description: "No pdf file is stored with insurance-case"
                            put:
                              tags:
                              - "insurance-case"
                              operationId: "put-insurance-case-pdf"
                              summary: "Add or update the pdf file stored with insurance-case"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                description: "File data to store"
                                required: true
                                content:
                                  '*/*':
                                    schema:
                                      type: "string"
                                      format: "binary"
                              responses:
                                "204":
                                  description: "The file is uploaded"
                            post:
                              tags:
                              - "insurance-case"
                              operationId: "post-insurance-case-pdf"
                              summary: "Add or update the pdf file stored with insurance-case"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                description: "File data to store"
                                required: true
                                content:
                                  '*/*':
                                    schema:
                                      type: "string"
                                      format: "binary"
                              responses:
                                "204":
                                  description: "The file is uploaded"
                            delete:
                              tags:
                              - "insurance-case"
                              operationId: "delete-insurance-case-pdf"
                              summary: "Delete the pdf file stored with insurance-case"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "204":
                                  description: "The file has been deleted"
                          /insurance-cases/{id}/thumb-nail:
                            get:
                              tags:
                              - "insurance-case"
                              operationId: "get-insurance-case-thumb_nail"
                              summary: "Retrieve the thumb_nail file stored with insurance-case"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "200":
                                  description: "Contents of the stored file"
                                  content:
                                    '*/*':
                                      schema:
                                        type: "string"
                                        format: "binary"
                                "404":
                                  description: "No thumb_nail file is stored with insurance-case"
                            put:
                              tags:
                              - "insurance-case"
                              operationId: "put-insurance-case-thumb_nail"
                              summary: "Add or update the thumb_nail file stored with insurance-case"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                description: "File data to store"
                                required: true
                                content:
                                  '*/*':
                                    schema:
                                      type: "string"
                                      format: "binary"
                              responses:
                                "204":
                                  description: "The file is uploaded"
                            post:
                              tags:
                              - "insurance-case"
                              operationId: "post-insurance-case-thumb_nail"
                              summary: "Add or update the thumb_nail file stored with insurance-case"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              requestBody:
                                description: "File data to store"
                                required: true
                                content:
                                  '*/*':
                                    schema:
                                      type: "string"
                                      format: "binary"
                              responses:
                                "204":
                                  description: "The file is uploaded"
                            delete:
                              tags:
                              - "insurance-case"
                              operationId: "delete-insurance-case-thumb_nail"
                              summary: "Delete the thumb_nail file stored with insurance-case"
                              parameters:
                              - name: "id"
                                in: "path"
                                required: true
                                schema:
                                  type: "string"
                              responses:
                                "204":
                                  description: "The file has been deleted"
                        components:
                          schemas:
                            Link:
                              type: "object"
                              properties:
                                href:
                                  type: "string"
                              required:
                              - "href"
                            problemDetail:
                              type: "object"
                              properties:
                                type:
                                  type: "string"
                                title:
                                  type: "string"
                                status:
                                  type: "integer"
                                detail:
                                  type: "string"
                                instance:
                                  type: "string"
                            problemDetailWithProperty:
                              allOf:
                              - $ref: "#/components/schemas/problemDetail"
                              - type: "object"
                                properties:
                                  property:
                                    type: "string"
                                required:
                                - "property"
                            problemDetailWithErrors:
                              allOf:
                              - $ref: "#/components/schemas/problemDetail"
                              - type: "object"
                                properties:
                                  errors:
                                    type: "array"
                                    items:
                                      oneOf:
                                      - $ref: "#/components/schemas/problemDetail"
                                      - $ref: "#/components/schemas/problemDetailWithProperty"
                                required:
                                - "errors"
                            problemDetailDiscriminator:
                              oneOf:
                              - $ref: "#/components/schemas/problemDetailWithErrors"
                              - $ref: "#/components/schemas/problemDetail"
                              discriminator:
                                propertyName: "type"
                                mapping:
                                  https://contentgrid.cloud/problems/input/validation: "#/components/schemas/problemDetailWithErrors"
                                  https://contentgrid.cloud/problems/input/duplicate-value: "#/components/schemas/problemDetail"
                                  https://contentgrid.cloud/problems/integrity/constraint-violation: "#/components/schemas/problemDetail"
                                  https://contentgrid.cloud/problems/invalid-request-body: "#/components/schemas/problemDetail"
                                  https://contentgrid.cloud/problems/invalid-request-body/type: "#/components/schemas/problemDetail"
                                  https://contentgrid.cloud/problems/invalid-request-body/json: "#/components/schemas/problemDetail"
                            page:
                              type: "object"
                              title: "Page metadata"
                              properties:
                                size:
                                  type: "integer"
                                  description: "Number of items shown on a single page"
                                total_items_estimate:
                                  type: "integer"
                                  description: "Estimated total number of items across all pages"
                                total_items_exact:
                                  type: "integer"
                                  description: "Exact total number of items across all pages (may be null\\
                                    \\ if no exact number could be calculated)"
                                  nullable: true
                                next_cursor:
                                  type: "string"
                                  description: "Cursor to access the next page of results (absent if there\\
                                    \\ is no next page)"
                                  example: "0msa4pz0"
                                prev_cursor:
                                  type: "string"
                                  description: "Cursor to access the previous page of results (absent if there\\
                                    \\ is no previous page)"
                                  example: "1mlpulv1"
                              required:
                              - "size"
                              - "total_items_estimate"
                              - "total_items_exact"
                            partyResponse:
                              type: "object"
                              properties:
                                id:
                                  type: "string"
                                vat:
                                  type: "string"
                                name:
                                  type: "string"
                                summary:
                                  $ref: "#/components/schemas/ContentInfo"
                                _links:
                                  type: "object"
                                  properties:
                                    self:
                                      $ref: "#/components/schemas/Link"
                                  required:
                                  - "self"
                              required:
                              - "id"
                              - "vat"
                              - "name"
                              - "summary"
                              - "_links"
                            insurance-caseResponse:
                              type: "object"
                              properties:
                                id:
                                  type: "string"
                                case_number:
                                  type: "integer"
                                  format: "int64"
                                created:
                                  type: "string"
                                  nullable: true
                                  format: "date-time"
                                pdf:
                                  $ref: "#/components/schemas/ContentInfo"
                                thumb_nail:
                                  $ref: "#/components/schemas/ContentInfo"
                                _links:
                                  type: "object"
                                  properties:
                                    self:
                                      $ref: "#/components/schemas/Link"
                                  required:
                                  - "self"
                              required:
                              - "id"
                              - "case_number"
                              - "created"
                              - "pdf"
                              - "thumb_nail"
                              - "_links"
                            partyCollection:
                              type: "object"
                              properties:
                                _embedded:
                                  type: "object"
                                  properties:
                                    item:
                                      type: "array"
                                      items:
                                        $ref: "#/components/schemas/partyResponse"
                                page:
                                  $ref: "#/components/schemas/page"
                              required:
                              - "page"
                            insurance-caseCollection:
                              type: "object"
                              properties:
                                _embedded:
                                  type: "object"
                                  properties:
                                    item:
                                      type: "array"
                                      items:
                                        $ref: "#/components/schemas/insurance-caseResponse"
                                page:
                                  $ref: "#/components/schemas/page"
                              required:
                              - "page"
                            partyPostBody:
                              type: "object"
                              properties:
                                vat:
                                  type: "string"
                                name:
                                  type: "string"
                                subsidiary:
                                  type: "string"
                                  nullable: true
                              required:
                              - "vat"
                              - "name"
                            partyPostMultipartFormDataBody:
                              type: "object"
                              properties:
                                vat:
                                  type: "string"
                                name:
                                  type: "string"
                                summary:
                                  type: "string"
                                  format: "binary"
                                subsidiary:
                                  type: "string"
                                  nullable: true
                              required:
                              - "vat"
                              - "name"
                            partyPutBody:
                              type: "object"
                              properties:
                                name:
                                  type: "string"
                                summary:
                                  $ref: "#/components/schemas/ContentInfoPUT"
                              required:
                              - "name"
                            partyPatchBody:
                              type: "object"
                              properties:
                                name:
                                  type: "string"
                                summary:
                                  $ref: "#/components/schemas/ContentInfoPATCH"
                            insurance-casePostBody:
                              type: "object"
                              properties:
                                case_number:
                                  type: "integer"
                                  format: "int64"
                                created:
                                  type: "string"
                                  nullable: true
                                  format: "date-time"
                                owning_party:
                                  type: "string"
                              required:
                              - "case_number"
                              - "owning_party"
                            insurance-casePostMultipartFormDataBody:
                              type: "object"
                              properties:
                                case_number:
                                  type: "integer"
                                  format: "int64"
                                created:
                                  type: "string"
                                  nullable: true
                                  format: "date-time"
                                pdf:
                                  type: "string"
                                  format: "binary"
                                thumb_nail:
                                  type: "string"
                                  format: "binary"
                                owning_party:
                                  type: "string"
                              required:
                              - "case_number"
                              - "owning_party"
                            insurance-casePutBody:
                              type: "object"
                              properties:
                                created:
                                  type: "string"
                                  nullable: true
                                  format: "date-time"
                                pdf:
                                  $ref: "#/components/schemas/ContentInfoPUT"
                                thumb_nail:
                                  $ref: "#/components/schemas/ContentInfoPUT"
                            insurance-casePatchBody:
                              type: "object"
                              properties:
                                created:
                                  type: "string"
                                  nullable: true
                                  format: "date-time"
                                pdf:
                                  $ref: "#/components/schemas/ContentInfoPATCH"
                                thumb_nail:
                                  $ref: "#/components/schemas/ContentInfoPATCH"
                            ContentInfo:
                              type: "object"
                              nullable: true
                              properties:
                                length:
                                  type: "integer"
                                  format: "int64"
                                mimetype:
                                  type: "string"
                                  example: "application/pdf"
                                filename:
                                  type: "string"
                                  nullable: true
                                  example: "example.pdf"
                              required:
                              - "length"
                              - "mimetype"
                              - "filename"
                            ContentInfoPUT:
                              type: "object"
                              properties:
                                mimetype:
                                  type: "string"
                                  example: "application/pdf"
                                filename:
                                  type: "string"
                                  nullable: true
                                  example: "example.pdf"
                              required:
                              - "mimetype"
                            ContentInfoPATCH:
                              type: "object"
                              properties:
                                mimetype:
                                  type: "string"
                                  example: "application/pdf"
                                filename:
                                  type: "string"
                                  nullable: true
                                  example: "example.pdf"
                        """;
        // @formatter:on
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
