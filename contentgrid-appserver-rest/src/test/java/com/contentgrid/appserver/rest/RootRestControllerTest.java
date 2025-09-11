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
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.spring.test.WithMockJwt;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "contentgrid.thunx.abac.source=none")
@AutoConfigureMockMvc(printOnlyOnFailure = false)
@WithMockJwt
class RootRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
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
                                .linkName(LinkName.of("persons"))
                                .build())
                        .entity(Entity.builder()
                                .name(EntityName.of("invoice"))
                                .table(TableName.of("invoice"))
                                .pathSegment(PathSegmentName.of("invoices"))
                                .linkName(LinkName.of("invoices"))
                                .build())
                        .entity(Entity.builder()
                                .name(EntityName.of("invoice-item"))
                                .table(TableName.of("invoice_item"))
                                .pathSegment(PathSegmentName.of("invoice-items"))
                                .linkName(LinkName.of("invoice-items"))
                                .build())
                        .build());
        mockMvc.perform(get("/").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/"))
                .andExpect(jsonPath("$._links.profile.href").value("http://localhost/profile"))
                .andExpect(jsonPath("$._links.cg:entity[?(@.name=='persons')].href").value("http://localhost/persons"))
                .andExpect(jsonPath("$._links.cg:entity[?(@.name=='invoices')].href").value("http://localhost/invoices"))
                .andExpect(jsonPath("$._links.cg:entity[?(@.name=='invoice-items')].href").value("http://localhost/invoice-items"))
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
                .andExpect(jsonPath("$._links.curies").doesNotExist()); // no curies, because there is no link with a curie prefix
    }

}