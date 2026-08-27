package com.contentgrid.appserver.rest.entity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.MultivalueAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.appserver.rest.test.ProblemDetailsMockMvcMatchers;
import com.contentgrid.appserver.rest.test.TestApplication;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = {TestApplication.class, MultivalueAttributeAllowedValuesRestTest.TestConfig.class})
@AutoConfigureMockMvc
@WithMockJwt
class MultivalueAttributeAllowedValuesRestTest {

    private static final SimpleAttribute DOCUMENT_ID = SimpleAttribute.builder()
            .name(AttributeName.of("document_id"))
            .column(ColumnName.of("document_id"))
            .type(Type.UUID)
            .flag(ReadOnlyFlag.INSTANCE)
            .build();

    private static final MultivalueAttribute DOCUMENT_TAGS = MultivalueAttribute.builder()
            .name(AttributeName.of("tags"))
            .column(ColumnName.of("tags"))
            .itemType(Type.TEXT)
            .constraint(Constraint.allowedValues(List.of("urgent", "vip")))
            .build();

    private static final Entity DOCUMENT = Entity.builder()
            .name(EntityName.of("document"))
            .table(TableName.of("document"))
            .pathSegment(PathSegmentName.of("documents"))
            .linkName(LinkName.of("document"))
            .primaryKey(DOCUMENT_ID)
            .attribute(DOCUMENT_TAGS)
            .build();

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("allowed-tags-application"))
            .entity(DOCUMENT)
            .build();

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        ApplicationResolver constrainedApplicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private TableCreator tableCreator;

    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);
    }

    @AfterEach
    void teardown() {
        tableCreator.dropTables(APPLICATION);
    }

    @Test
    void createWithDisallowedTagElementIsRejected() throws Exception {
        mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("tags", List.of("urgent", "forbidden")))))
                .andExpect(status().isBadRequest())
                .andExpect(ProblemDetailsMockMvcMatchers.validationConstraintViolation()
                        .withError(error -> error
                                .withType("https://contentgrid.cloud/problems/input/validation/allowed-values")
                                .withTitle("Value is not allowed")
                                .withDetail("The value must be one of the allowed values [urgent, vip]")
                                .withField("field", "tags")
                                .withField("allowed_values", List.of("urgent", "vip"))
                        ));
    }

    @Test
    void updateWithDisallowedTagElementIsRejected() throws Exception {
        var location = mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("tags", List.of("urgent")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        mockMvc.perform(put(location)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("tags", List.of("forbidden")))))
                .andExpect(status().isBadRequest())
                .andExpect(ProblemDetailsMockMvcMatchers.validationConstraintViolation()
                        .withError(error -> error
                                .withType("https://contentgrid.cloud/problems/input/validation/allowed-values")
                                .withTitle("Value is not allowed")
                                .withDetail("The value must be one of the allowed values [urgent, vip]")
                                .withField("field", "tags")
                                .withField("allowed_values", List.of("urgent", "vip"))
                        ));

        mockMvc.perform(get(location).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags").value("urgent"));
    }
}
