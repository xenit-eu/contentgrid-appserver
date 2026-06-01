package com.contentgrid.appserver.webjars;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.contentgrid.appserver.webjars.WebjarsApplicationTest.TestRestController;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;


@WebMvcTest
@ContextConfiguration(classes = {WebjarsRestConfiguration.class, TestRestController.class})
@AutoConfigureMockMvc(printOnlyOnFailure = false)
class WebjarsApplicationTest {

    @RestController
    static class TestRestController {

        @GetMapping("/")
        ResponseEntity<String> root() {
            return ResponseEntity.ok("Root");
        }

        @GetMapping("/{entity:invoices|products}")
        ResponseEntity<String> entityCollection(@PathVariable("entity") String entity) {
            return ResponseEntity.ok("%s collection".formatted(entity));
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void rootWithoutAcceptHeaderHttpOk() throws Exception {
        this.mockMvc.perform(get("/"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("Root"));
    }

    @Test
    void rootWithTextHtmlAcceptHeaderHttpFound() throws Exception {
        this.mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.LOCATION, "http://localhost/webjars/hal-explorer/index.html#uri=/"));
    }

    @Test
    void rootWithDifferentAcceptHeaderHttpOk() throws Exception {
        this.mockMvc.perform(get("/").accept(MediaType.ALL))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("Root"));
    }

    @Test
    void explorerWithTextHtmlAcceptHeaderHttpFound() throws Exception {
        this.mockMvc.perform(get("/explorer").accept(MediaType.TEXT_HTML))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.LOCATION, "http://localhost/webjars/hal-explorer/index.html#uri=/"));
    }

    @Test
    void explorerIndexHtmlHttpFound() throws Exception {
        this.mockMvc.perform(get("/explorer/index.html"))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.LOCATION, "http://localhost/webjars/hal-explorer/index.html#uri=/"));
    }

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
