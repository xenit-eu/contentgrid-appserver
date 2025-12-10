package com.contentgrid.appserver.integration.test.validation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApi;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import com.contentgrid.appserver.rest.test.WithMockJwt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = InvoicingApiApplication.class, properties = {
        "contentgrid.events.rabbitmq.enabled=false",
})
@AutoConfigureMockMvc(printOnlyOnFailure = false)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@WithMockJwt
class ContentGridSpringDataRestValidationConfigurationIntegrationTest {

    private static final String URI_LIST_MIMETYPE = "text/uri-list";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    InvoicingApi invoicingApi;

    @Nested
    class PropertyValidation {
        @Test
        void allowsValidCustomerCreate() throws Exception {
            mockMvc.perform(post("/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "vat": "BE123"
                        }
                        """)
            ).andExpect(status().isCreated());
        }

        @Test
        void allowsValidCustomerUpdate() throws Exception {
            var customerId = invoicingApi.createCustomer(null, "ABC-123").getIdentity().getEntityId();

            mockMvc.perform(put("/customers/{id}", customerId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "vat": "BE456"
                        }
                        """)
            ).andExpect(status().isNoContent());
        }

        @Test
        void allowsValidCustomerPatch() throws Exception {
            var customerId = invoicingApi.createCustomer(null, "ABC-124").getIdentity().getEntityId();

            mockMvc.perform(patch("/customers/{id}", customerId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "XYZ"
                        }
                        """)
            ).andExpect(status().isNoContent());
        }

        @Test
        void rejectsInvalidCustomerCreate() throws Exception {
            mockMvc.perform(post("/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "XYZ"
                        }
                        """)
            ).andExpect(status().isBadRequest());
        }

        @Test
        void rejectsInvalidCustomerUpdate() throws Exception {
            var customerId = invoicingApi.createCustomer(null, "ABC-125").getIdentity().getEntityId();

            mockMvc.perform(put("/customers/{id}", customerId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "XYZ"
                        }
                        """)
            ).andExpect(status().isBadRequest());
        }

        @Test
        void rejectsInvalidCustomerPatch() throws Exception {
            var customerId = invoicingApi.createCustomer(null, "ABC-126").getIdentity().getEntityId();

            mockMvc.perform(put("/customers/{id}", customerId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "vat": null
                        }
                        """)
            ).andExpect(status().isBadRequest());
        }
    }

    @Nested
    class RequiredRelation {


        @Test
        void allowsValidInvoiceCreate_withRelation() throws Exception {
            var customerId = invoicingApi.createCustomer(null, "XYZ-1").getIdentity().getEntityId();

            mockMvc.perform(post("/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "number": "123",
                                "counterparty": "http://localhost/customers/%s"
                            }
                            """.formatted(customerId))
            ).andExpect(status().isCreated());
        }

        @Test
        void rejectsInvalidInvoiceCreate_missingRelation() throws Exception {
            mockMvc.perform(post("/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "number": "123"
                            }
                            """)
            ).andExpect(status().isBadRequest());
        }

        @Test
        void rejectsInvalidInvoiceCreate_invalidRelation() throws Exception {
            mockMvc.perform(post("/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "number": "123",
                                "counterparty": "XXXX"
                            }
                            """)
            ).andExpect(status().isBadRequest());
        }

        @Test
        void rejectsInvalidInvoiceCreate_nonExistingRelation() throws Exception {
            mockMvc.perform(post("/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "number": "123",
                                "counterparty": "http://localhost/customers/01bb4210-523b-11ee-9553-e76392218fe8"
                            }
                            """)
            ).andExpect(status().isBadRequest());
        }

        @Test
        void rejectsInvalidInvoiceRelationDelete_requiredRelation() throws Exception {
            var customerId = invoicingApi.createCustomer(null, "XYZ-4").getIdentity().getEntityId();
            var invoiceId = invoicingApi.createInvoice("XYZ-4", false, false, customerId, null).getIdentity().getEntityId();

            mockMvc.perform(delete("/invoices/{id}/counterparty", invoiceId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsInvalidInvoiceRelationPut_requiredRelation() throws Exception {
            var customerId = invoicingApi.createCustomer(null, "XYZ-5").getIdentity().getEntityId();
            var invoiceId = invoicingApi.createInvoice("XYZ-5", false, false, customerId, null).getIdentity().getEntityId();

            mockMvc.perform(put("/invoices/{id}/counterparty", invoiceId)
                            .contentType(URI_LIST_MIMETYPE)
                            .content("")
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        void allowsValidInvoiceRelationPut_requiredRelation() throws Exception {
            var customerId = invoicingApi.createCustomer(null, "XYZ-6").getIdentity().getEntityId();
            var invoiceId = invoicingApi.createInvoice("XYZ-6", false, false, customerId, null).getIdentity().getEntityId();
            var customer2Id = invoicingApi.createCustomer(null, "XYZ-7").getIdentity().getEntityId();

            mockMvc.perform(put("/invoices/{id}/counterparty", invoiceId)
                            .contentType(URI_LIST_MIMETYPE)
                            .content("http://localhost/customers/" + customer2Id)
                    )
                    .andExpect(status().is2xxSuccessful());
        }
    }
}