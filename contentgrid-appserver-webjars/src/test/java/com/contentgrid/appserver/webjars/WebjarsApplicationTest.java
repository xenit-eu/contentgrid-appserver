package com.contentgrid.appserver.webjars;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;


@WebMvcTest
@ContextConfiguration(classes = WebjarsRestConfiguration.class)
@AutoConfigureMockMvc(printOnlyOnFailure = false)
class WebjarsApplicationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void webjarsHalExplorerWithoutVersionReturnsHttpOk() throws Exception {
        this.mockMvc.perform(get("/webjars/hal-explorer/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_HTML));
    }

    @Test
    void webjarsHalExplorerWithInvalidVersionReturnsHttpNotFound() throws Exception {
        this.mockMvc.perform(get("/webjars/hal-explorer/1.0.4/index.html"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void webjarsSwaggerUIwithoutVersionReturnsHttpOk() throws Exception {
        this.mockMvc.perform(get("/webjars/swagger-ui/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_HTML));

    }

    @Test
    void webjarsSwaggerUIwithInvalidVersionReturnsHttpNotFound() throws Exception {
        this.mockMvc.perform(get("/webjars/swagger-ui/1.2.3.4/index.html"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void customSwaggerInitializer() throws Exception {
        this.mockMvc.perform(get("/webjars/swagger-ui/swagger-initializer.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())

                // should NOT serve any default config
                .andExpect(MockMvcResultMatchers.content().string(not(Matchers.containsString("petstore"))))

                // but use the contentgrid url: "/openapi.yml"
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("url: \"/openapi.yml\"")));
    }


}
