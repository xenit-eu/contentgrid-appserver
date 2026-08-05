package com.contentgrid.appserver.integration.test.opa;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.security.authority.GatewayAuthClaimNames;
import com.contentgrid.appserver.security.authority.GatewayJwtAuthenticationDetailsConverter;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApi;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "contentgrid.events.rabbitmq.enabled=false",
        "contentgrid.thunx.abac.source=opa",
        "opa.query=data.contentgrid.appserver.allow == true",
})
@ContextConfiguration(classes = InvoicingApiApplication.class)
@AutoConfigureMockMvc(printOnlyOnFailure = false)
class OpaResidualAuthorizationTest {

    private static final Logger logger = LoggerFactory.getLogger(OpaResidualAuthorizationTest.class);

    private static final String JWT_ISSUER = "https://tenant.example.com";

    @Container
    static GenericContainer<?> opa = new GenericContainer<>("openpolicyagent/opa:1.15.2-static")
            .withCommand("run", "--server", "--log-format=json-pretty", "--set=decision_logs.console=true",
                    "--addr", "0.0.0.0:8181")
            .withExposedPorts(8181)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forHttp("/health").forPort(8181));

    @DynamicPropertySource
    static void opaProperties(DynamicPropertyRegistry registry) {
        registry.add("opa.service.url",
                () -> "http://%s:%d".formatted(opa.getHost(), opa.getMappedPort(8181)));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoicingApi invoicingApi;

    @AfterEach
    void cleanupTestData() {
        invoicingApi.deleteAll();
    }

    private JwtRequestPostProcessor authenticatedAs(String subject) {
        return jwt()
                .jwt(builder -> builder
                        .subject(subject)
                        .issuer(JWT_ISSUER)
                        .claim(GatewayAuthClaimNames.AUTH_KIND, GatewayAuthClaimNames.AUTH_KIND_USER)
                        .claim(GatewayAuthClaimNames.AUTH_PRINCIPAL, Map.of(
                                GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_USER,
                                "iss", JWT_ISSUER,
                                "sub", subject
                        )))
                .authorities(new GatewayJwtAuthenticationDetailsConverter());
    }

    private UUID createCustomer(String vat, long totalSpend) throws Exception {
        var response = mockMvc.perform(post("/customers")
                        .with(authenticatedAs("creator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "vat": "%s",
                                    "total_spend": %d
                                }
                                """.formatted(vat, totalSpend)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();

        var location = Objects.requireNonNull(response.getHeader(HttpHeaders.LOCATION));
        var matches = new UriTemplate("{scheme}://{host}/customers/{id}").match(location);
        return UUID.fromString(matches.get("id"));
    }

    @Test
    void listCustomers_returnsOnlyRowsMatchingResidualPredicate() throws Exception {
        var matchingVat = "BE0000000001";
        var nonMatchingVat = "BE0000000002";
        createCustomer(matchingVat, 50);
        createCustomer(nonMatchingVat, 500);

        mockMvc.perform(get("/customers").with(authenticatedAs("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total_items_exact").value(1))
                .andExpect(jsonPath("$._embedded.['item'].length()").value(1))
                .andExpect(jsonPath("$._embedded.['item'][0].vat").value(matchingVat));
    }

    @Test
    void getCustomerById_matchingResidualPredicate_returnsHttp200() throws Exception {
        var matchingId = createCustomer("BE0000000003", 100);

        mockMvc.perform(get("/customers/{id}", matchingId).with(authenticatedAs("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vat").value("BE0000000003"));
    }

    @Test
    void getCustomerById_notMatchingResidualPredicate_returnsHttp403() throws Exception {
        // The row exists (unlike a genuinely unknown id, which the query engine reports as 404), but the
        // residual predicate excludes it: JOOQQueryEngine#findById fetches by primary key regardless of the
        // predicate and evaluates it as a computed "_allow_read" select column, throwing
        // PermissionDeniedException (-> 403) rather than treating it as not-found when that column is false.
        var nonMatchingId = createCustomer("BE0000000004", 500);

        mockMvc.perform(get("/customers/{id}", nonMatchingId).with(authenticatedAs("reader")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCustomers_withoutAuthentication_isRejected() throws Exception {
        createCustomer("BE0000000005", 50);

        mockMvc.perform(get("/customers"))
                .andExpect(status().is(403));
    }
}
