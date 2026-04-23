package com.contentgrid.appserver.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.rest.test.TestApplication;
import com.contentgrid.appserver.registry.ApplicationResolver;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "contentgrid.events.rabbitmq.enabled=false",
})
@AutoConfigureMockMvc(printOnlyOnFailure = false)
class RootRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationResolver resolver;

    @Test
    void getRoot() throws Exception {
        Mockito.when(resolver.resolve(Mockito.any()))
                .thenReturn(Application.builder()
                        .name(ApplicationName.of("test-application"))
                        .entity(Entity.builder()
                                .name(EntityName.of("person"))
                                .table(TableName.of("person"))
                                .pathSegment(PathSegmentName.of("persons"))
                                .linkName(LinkName.of("person"))
                                .build())
                        .entity(Entity.builder()
                                .name(EntityName.of("invoice"))
                                .table(TableName.of("invoice"))
                                .pathSegment(PathSegmentName.of("invoices"))
                                .linkName(LinkName.of("invoice"))
                                .build())
                        .entity(Entity.builder()
                                .name(EntityName.of("invoice-item"))
                                .table(TableName.of("invoice_item"))
                                .pathSegment(PathSegmentName.of("invoice-items"))
                                .linkName(LinkName.of("invoice-item"))
                                .build())
                        .build());
        mockMvc.perform(get("/").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/"))
                .andExpect(jsonPath("$._links.profile.href").value("http://localhost/profile"))
                .andExpect(jsonPath("$._links.cg:entity[?(@.name=='person')].href").value("http://localhost/persons"))
                .andExpect(jsonPath("$._links.cg:entity[?(@.name=='invoice')].href").value("http://localhost/invoices"))
                .andExpect(jsonPath("$._links.cg:entity[?(@.name=='invoice-item')].href").value("http://localhost/invoice-items"))
                .andExpect(jsonPath("$._links.automation:registrations.href").value("http://localhost/.contentgrid/automations"))
                .andExpect(jsonPath("$._links.curies").isArray());
    }

    @Test
    void getRootNoEntities() throws Exception {
        Mockito.when(resolver.resolve(Mockito.any()))
                .thenReturn(Application.builder()
                        .name(ApplicationName.of("test-application"))
                        .build());
        mockMvc.perform(get("/").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/"))
                .andExpect(jsonPath("$._links.profile.href").value("http://localhost/profile"))
                .andExpect(jsonPath("$._links.cg:entity").doesNotExist())
                .andExpect(jsonPath("$._links.automation:registrations.href").value("http://localhost/.contentgrid/automations"))
                .andExpect(jsonPath("$._links.curies").isArray()); // curies are still present, because there is a link with automation curie prefix
    }

}