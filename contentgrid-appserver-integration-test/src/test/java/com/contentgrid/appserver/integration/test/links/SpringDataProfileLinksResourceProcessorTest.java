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
class SpringDataProfileLinksResourceProcessorTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void entityLinkRelAddedToProfile() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/profile"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("""
                        {
                            _links: {
                                "cg:entity": [
                                    {
                                        name: "customer",
                                        href: "http://localhost/profile/customers"
                                    },
                                    {
                                        name: "invoice",
                                        href: "http://localhost/profile/invoices"
                                    },
                                    {
                                        name: "order",
                                        href: "http://localhost/profile/orders"
                                    },
                                    {
                                        name: "promotion",
                                        href: "http://localhost/profile/promotions"
                                    },
                                    {
                                        name: "shipping-address",
                                        href: "http://localhost/profile/shipping-addresses"
                                    },
                                    {
                                        name: "shipping-label",
                                        href: "http://localhost/profile/shipping-labels"
                                    },
                                    {
                                        name: "refund",
                                        href: "http://localhost/profile/refunds"
                                    }
                                ]
                            }
                        }
                        """));
    }
}