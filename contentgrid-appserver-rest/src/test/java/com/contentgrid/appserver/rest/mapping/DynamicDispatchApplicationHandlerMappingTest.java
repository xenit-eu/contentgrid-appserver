package com.contentgrid.appserver.rest.mapping;

import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.autoconfigure.rest.ContentGridRestAutoConfiguration;
import com.contentgrid.appserver.autoconfigure.security.DefaultSecurityAutoConfiguration;
import com.contentgrid.appserver.registry.ApplicationNameExtractor;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType.PropertyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootTest(properties = {
        "contentgrid.thunx.abac.source=none",
        "contentgrid.security.unauthenticated.allow=true",
        "contentgrid.security.csrf.disabled=true",
})
@AutoConfigureMockMvc
@EnableAutoConfiguration(exclude = {ContentGridRestAutoConfiguration.class, DefaultSecurityAutoConfiguration.class})
class DynamicDispatchApplicationHandlerMappingTest {

    private static final Entity TEST_ENTITY = Entity.builder()
            .name(EntityName.of("test-entity"))
            .table(TableName.of("test_entity"))
            .pathSegment(PathSegmentName.of("test-entities"))
            .attribute(ContentAttribute.builder()
                    .name(AttributeName.of("content"))
                    .pathSegment(PathSegmentName.of("content"))
                    .linkName(LinkName.of("content"))
                    .idColumn(ColumnName.of("content_id"))
                    .lengthColumn(ColumnName.of("content_size"))
                    .mimetypeColumn(ColumnName.of("content_mimetype"))
                    .filenameColumn(ColumnName.of("content_filename"))
                    .build())
            .linkName(LinkName.of("test-entity"))
            .build();
    private static final Entity OTHER_ENTITY = Entity.builder()
            .name(EntityName.of("other"))
            .table(TableName.of("other"))
            .pathSegment(PathSegmentName.of("others"))
            .linkName(LinkName.of("other"))
            .build();
    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("default"))
            .entity(TEST_ENTITY)
            .entity(OTHER_ENTITY)
            .relation(SourceOneToOneRelation.builder()
                    .sourceEndPoint(RelationEndPoint.builder()
                            .entity(TEST_ENTITY.getName())
                            .name(RelationName.of("to_one_other"))
                            .pathSegment(PathSegmentName.of("to-one-other"))
                            .linkName(LinkName.of("to-one-other"))
                            .build())
                    .targetEndPoint(RelationEndPoint.builder()
                            .entity(OTHER_ENTITY.getName())
                            .build())
                    .targetReference(ColumnName.of("other_id"))
                    .build())
            .relation(OneToManyRelation.builder()
                    .sourceEndPoint(RelationEndPoint.builder()
                            .entity(TEST_ENTITY.getName())
                            .name(RelationName.of("to_many_other"))
                            .pathSegment(PathSegmentName.of("to-many-other"))
                            .linkName(LinkName.of("to-many-other"))
                            .build())
                    .targetEndPoint(RelationEndPoint.builder()
                            .entity(OTHER_ENTITY.getName())
                            .build())
                    .sourceReference(ColumnName.of("test_id"))
                    .build())
            .build();

    @SpringBootApplication
    public static class TestApplication {

        @Bean
        ApplicationNameExtractor applicationNameExtractor() {
            return request -> ApplicationName.of("default");
        }

        @Bean
        ApplicationResolver applicationResolver() {
            return name -> APPLICATION;
        }

        @Bean
        TestController testController() {
            return new TestController();
        }

        @Bean
        WebMvcConfigurer corsConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addCorsMappings(CorsRegistry registry) {
                    registry.addMapping("/**").allowedOrigins("http://localhost:1234").allowedMethods("*");
                }
            };
        }

    }

    @RestController
    static class TestController {

        @GetMapping(value = "/{entityName}", produces = "application/json")
        ResponseEntity<String> getEntity(
                @PathVariable String entityName
        ) {
            return ResponseEntity.ok("Entity %s".formatted(
                    APPLICATION.getEntityByPathSegment(PathSegmentName.of(entityName)).orElseThrow().getName()
                            .getValue()));
        }

        @GetMapping("/{entityName}/{contentAttributeName}")
        @SpecializedOnPropertyType(type = PropertyType.CONTENT_ATTRIBUTE, entityPathVariable = "entityName", propertyPathVariable = "contentAttributeName")
        ResponseEntity<String> getContent(
                @PathVariable String entityName,
                @PathVariable String contentAttributeName
        ) {
            var content = APPLICATION.getEntityByPathSegment(PathSegmentName.of(entityName))
                    .orElseThrow()
                    .getContentByPathSegment(PathSegmentName.of(contentAttributeName))
                    .orElseThrow();
            return ResponseEntity.ok("Content %s".formatted(content.getName().getValue()));
        }

        @RequestMapping(method = {RequestMethod.POST,
                RequestMethod.PUT}, value = "/{entity}/{contentName}", consumes = "*/*")
        @SpecializedOnPropertyType(type = PropertyType.CONTENT_ATTRIBUTE, entityPathVariable = "entity", propertyPathVariable = "contentName")
        ResponseEntity<String> setContent(
                @PathVariable String entity,
                @PathVariable String contentName
        ) {
            var content = APPLICATION.getEntityByPathSegment(PathSegmentName.of(entity))
                    .orElseThrow()
                    .getContentByPathSegment(PathSegmentName.of(contentName))
                    .orElseThrow();
            return ResponseEntity.ok("Put content %s using direct".formatted(content.getName().getValue()));
        }

        @RequestMapping(method = {RequestMethod.POST,
                RequestMethod.PUT}, value = "/{entity}/{contentName}", consumes = "multipart/form-data")
        @SpecializedOnPropertyType(type = PropertyType.CONTENT_ATTRIBUTE, entityPathVariable = "entity", propertyPathVariable = "contentName")
        ResponseEntity<String> setContent(
                @PathVariable String entity,
                @PathVariable String contentName,
                @RequestPart MultipartFile file
        ) {
            var content = APPLICATION.getEntityByPathSegment(PathSegmentName.of(entity))
                    .orElseThrow()
                    .getContentByPathSegment(PathSegmentName.of(contentName))
                    .orElseThrow();
            return ResponseEntity.ok("Put content %s using multipart".formatted(content.getName().getValue()));
        }

        @GetMapping("/{entityName}/{relationName}")
        @SpecializedOnPropertyType(type = {PropertyType.TO_MANY_RELATION,
                PropertyType.TO_ONE_RELATION}, entityPathVariable = "entityName", propertyPathVariable = "relationName")
        ResponseEntity<String> getRelation(
                @PathVariable String entityName,
                @PathVariable String relationName
        ) {
            var relation = APPLICATION.getRelationForPath(PathSegmentName.of(entityName),
                    PathSegmentName.of(relationName)).orElseThrow();
            return ResponseEntity.ok("Relation %s".formatted(relation.getSourceEndPoint().getName().getValue()));
        }

        @PostMapping(value = "/{entityName}/{relationName}", consumes = {"application/json", "text/uri-list"})
        @SpecializedOnPropertyType(type = {
                PropertyType.TO_MANY_RELATION}, entityPathVariable = "entityName", propertyPathVariable = "relationName")
        ResponseEntity<String> postRelation(
                @PathVariable String entityName,
                @PathVariable String relationName
        ) {
            var relation = APPLICATION.getRelationForPath(PathSegmentName.of(entityName),
                    PathSegmentName.of(relationName)).orElseThrow();
            return ResponseEntity.ok("Add to relation %s".formatted(relation.getSourceEndPoint().getName().getValue()));
        }

        @PutMapping(value = "/{entityName}/{relationName}", consumes = {"application/json", "text/uri-list"})
        @SpecializedOnPropertyType(type = {
                PropertyType.TO_ONE_RELATION}, entityPathVariable = "entityName", propertyPathVariable = "relationName")
        ResponseEntity<String> putRelation(
                @PathVariable String entityName,
                @PathVariable String relationName
        ) {
            var relation = APPLICATION.getRelationForPath(PathSegmentName.of(entityName),
                    PathSegmentName.of(relationName)).orElseThrow();
            return ResponseEntity.ok(
                    "Replace relation %s".formatted(relation.getSourceEndPoint().getName().getValue()));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resolveNormalMethods() throws Exception {
        mockMvc.perform(get("/test-entities"))
                .andExpect(status().isOk())
                .andExpect(content().string("Entity test-entity"));
    }

    @Test
    void resolveDisambiguatedMethods() throws Exception {
        mockMvc.perform(get("/test-entities/content"))
                .andExpect(status().isOk())
                .andExpect(content().string("Content content"));

        mockMvc.perform(get("/test-entities/to-one-other"))
                .andExpect(status().isOk())
                .andExpect(content().string("Relation to_one_other"));

        mockMvc.perform(get("/test-entities/to-many-other"))
                .andExpect(status().isOk())
                .andExpect(content().string("Relation to_many_other"));

        mockMvc.perform(get("/non-existing/item"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolvePreflightRequests() throws Exception {
        mockMvc.perform(options("/test-entities/content")
                        .header(HttpHeaders.ORIGIN, "http://localhost:1234/")
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, HttpMethod.GET.name()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString(HttpMethod.GET.name())))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString(HttpMethod.POST.name())))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString(HttpMethod.PUT.name())))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:1234/"));

        mockMvc.perform(options("/test-entities/to-one-other")
                        .header(HttpHeaders.ORIGIN, "http://localhost:1234/")
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, HttpMethod.GET.name()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString(HttpMethod.GET.name())))
                .andExpect(header().string(HttpHeaders.ALLOW, not(containsString(HttpMethod.POST.name()))))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString(HttpMethod.PUT.name())))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:1234/"));

        mockMvc.perform(options("/test-entities/to-many-other")
                        .header(HttpHeaders.ORIGIN, "http://localhost:1234/")
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, HttpMethod.GET.name()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString(HttpMethod.GET.name())))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString(HttpMethod.POST.name())))
                .andExpect(header().string(HttpHeaders.ALLOW, not(containsString(HttpMethod.PUT.name()))))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:1234/"));

    }

    @Test
    void resolveDisambiguatedMethodsWithConsumesMediatypes() throws Exception {
        mockMvc.perform(post("/test-entities/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Put content content using direct"));

        mockMvc.perform(post("/test-entities/to-one-other")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed()); // Can't POST to a to one relation, but you can GET or PUT

        mockMvc.perform(post("/test-entities/to-many-other")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Add to relation to_many_other"));

        mockMvc.perform(put("/test-entities/content")
                        .contentType("text/uri-list")
                        .content("xyz"))
                .andExpect(status().isOk())
                .andExpect(content().string("Put content content using direct"));

        mockMvc.perform(put("/test-entities/to-one-other")
                        .contentType("text/uri-list")
                        .content("xyz"))
                .andExpect(status().isOk())
                .andExpect(content().string("Replace relation to_one_other"));

        mockMvc.perform(put("/test-entities/to-many-other")
                        .contentType("text/uri-list")
                        .content("xyz"))
                .andExpect(status().isMethodNotAllowed()); // Can't PUT to a to many relation, but you can GET or POST

        mockMvc.perform(multipart("/test-entities/content")
                        .file("file", new byte[5])
                ).andExpect(status().isOk())
                .andExpect(content().string("Put content content using multipart"));

        mockMvc.perform(multipart("/test-entities/to-many-other")
                .file("file", new byte[5])
        ).andExpect(status().isUnsupportedMediaType());


    }

}