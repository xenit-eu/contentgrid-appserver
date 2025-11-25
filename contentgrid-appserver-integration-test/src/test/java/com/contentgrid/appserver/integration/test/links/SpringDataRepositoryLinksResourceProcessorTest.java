package com.contentgrid.appserver.integration.test.links;

import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest(properties = {
        "contentgrid.events.rabbitmq.enabled=false",
})
@ContextConfiguration(classes = {
        InvoicingApiApplication.class
})
@AutoConfigureMockMvc
@WithMockJwt
class SpringDataRepositoryLinksResourceProcessorTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void entityLinkRelAddedToRoot() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _links: {
                                "cg:entity": [
                                    {
                                        name: "customer",
                                        href: "http://localhost/customers"
                                    },
                                    {
                                        name: "invoice",
                                        href: "http://localhost/invoices"
                                    },
                                    {
                                        name: "order",
                                        href: "http://localhost/orders"
                                    },
                                    {
                                        name: "promotion",
                                        href: "http://localhost/promotions"
                                    },
                                    {
                                        name: "shipping-address",
                                        href: "http://localhost/shipping-addresses"
                                    },
                                    {
                                        name: "shipping-label",
                                        href: "http://localhost/shipping-labels"
                                    },
                                    {
                                        name: "refund",
                                        href: "http://localhost/refunds"
                                    }
                                ]
                            }
                        }
                        """));
    }

}