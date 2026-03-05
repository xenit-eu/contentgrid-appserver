package com.contentgrid.appserver.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.relations.flags.VisibleEndpointFlag;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.example.ContentgridApp;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.appserver.rest.InternalInverseRelationBlindOverwriteTest.TestConfig;
import com.contentgrid.appserver.rest.test.ProblemDetailsMockMvcMatchers;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {ContentgridApp.class, TestConfig.class}, properties = {
        "contentgrid.thunx.abac.source=none",
        "contentgrid.appserver.content-store.type=ephemeral",
        "contentgrid.events.rabbitmq.enabled=false"
})
@AutoConfigureMockMvc
@WithMockJwt
// Reproduction case for ACC-2394
class InternalInverseRelationBlindOverwriteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    TableCreator tableCreator;

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public SingleApplicationResolver singleApplicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }
    }

    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);
    }

    @AfterEach
    void teardown() {
        tableCreator.dropTables(APPLICATION);
    }

    @Test
    void testAddSameEmployeeToMultipleDepartmentsAsMembers() throws Exception {
        // Set up data with employee Alice in department Engineering
        String employeeResponse = mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Alice")
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String employeeUrl = objectMapper.readTree(employeeResponse)
                .at("/_links/self/href")
                .asText();

        String departmentEngineering = mockMvc.perform(post("/departments")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Engineering")
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String departmentEngineeringUrl = objectMapper.readTree(departmentEngineering)
                .at("/_links/self/href")
                .asText();

        String departmentManagementResponse = mockMvc.perform(post("/departments")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Management")
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String departmentManagementUrl = objectMapper.readTree(departmentManagementResponse)
                .at("/_links/self/href")
                .asText();

        mockMvc.perform(post(departmentEngineeringUrl + "/members")
                        .contentType("text/uri-list")
                        .content(employeeUrl))
                .andExpect(status().isNoContent());

        // Add Alice as member to Management
        // This blindly overwrites the internal many-to-one relation from Alice → Engineering
        // Expect BlindRelationOverwriteException → http 409
        mockMvc.perform(post(departmentManagementUrl + "/members")
                        .contentType("text/uri-list")
                        .content(employeeUrl))
                .andExpect(ProblemDetailsMockMvcMatchers.problemDetails()
                        .withStatusCode(HttpStatus.CONFLICT)
                        // existing-item should point to the place where you can fix it (i.e. remove alice from this dept first)
                        .withField("existing_item", departmentEngineeringUrl)
                        .withField("existing_relation", departmentEngineeringUrl+"/members")
                        // target-item points to the item that was the problem
                        .withField("target_item", employeeUrl)
                        // new-item points to the place where you tried to add it to
                        .withField("new_item", departmentManagementUrl)
                        .withField("new_relation", departmentManagementUrl+"/members")
                ).andExpect(jsonPath("$.target_relation").doesNotExist());
    }

    // Application model:
    //   - department
    //      - attr: name
    //      - rel: members [one-to-many, to employee, visible]
    //   - employee
    //      - attr: name
    //      - rel: _internal_department__members [many-to-one, to department, hidden]
    //

    private static final SimpleAttribute ATTR_EMPLOYEE_ID = SimpleAttribute.builder()
            .name(AttributeName.of("employee_id"))
            .column(ColumnName.of("employee_id"))
            .type(Type.UUID)
            .flag(ReadOnlyFlag.INSTANCE)
            .build();

    private static final SimpleAttribute ATTR_EMPLOYEE_NAME = SimpleAttribute.builder()
            .name(AttributeName.of("name"))
            .column(ColumnName.of("name"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .build();

    private static final Entity ENT_EMPLOYEE = Entity.builder()
            .name(EntityName.of("employee"))
            .table(TableName.of("employee"))
            .pathSegment(PathSegmentName.of("employees"))
            .linkName(LinkName.of("employees"))
            .primaryKey(ATTR_EMPLOYEE_ID)
            .attribute(ATTR_EMPLOYEE_NAME)
            .build();

    // Define Department entity
    private static final SimpleAttribute ATTR_DEPARTMENT_ID = SimpleAttribute.builder()
            .name(AttributeName.of("department_id"))
            .column(ColumnName.of("department_id"))
            .type(Type.UUID)
            .flag(ReadOnlyFlag.INSTANCE)
            .build();

    private static final SimpleAttribute ATTR_DEPARTMENT_NAME = SimpleAttribute.builder()
            .name(AttributeName.of("name"))
            .column(ColumnName.of("name"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .build();

    private static final Entity ENT_DEPARTMENT = Entity.builder()
            .name(EntityName.of("department"))
            .table(TableName.of("department"))
            .pathSegment(PathSegmentName.of("departments"))
            .linkName(LinkName.of("departments"))
            .primaryKey(ATTR_DEPARTMENT_ID)
            .attribute(ATTR_DEPARTMENT_NAME)
            .build();

    // Define Department -> Employee relation (one department to many employees as members)
    private static final OneToManyRelation REL_DEPARTMENT_TO_MEMBERS = OneToManyRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(ENT_DEPARTMENT.getName())
                    .name(RelationName.of("members"))
                    .pathSegment(PathSegmentName.of("members"))
                    .linkName(LinkName.of("members"))
                    .flag(VisibleEndpointFlag.INSTANCE)
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(ENT_EMPLOYEE.getName())
                    .name(RelationName.of("_internal_department__members"))
                    .pathSegment(null)
                    .linkName(null)
                    .flag(HiddenEndpointFlag.INSTANCE)
                    .build())
            .sourceReference(ColumnName.of("_department_id__members"))
            .build();

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("department-employee-app"))
            .entity(ENT_DEPARTMENT)
            .entity(ENT_EMPLOYEE)
            .relation(REL_DEPARTMENT_TO_MEMBERS)
            .build();

}
