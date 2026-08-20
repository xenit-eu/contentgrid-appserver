package com.contentgrid.appserver.integration.test.opa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.security.authority.GatewayAuthClaimNames;
import com.contentgrid.appserver.security.authority.GatewayJwtAuthenticationDetailsConverter;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApi;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
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
@ContextConfiguration(classes = {InvoicingApiApplication.class, OpaResidualAuthorizationTest.NoOpJwtDecoderConfiguration.class})
@AutoConfigureMockMvc(printOnlyOnFailure = false)
class OpaResidualAuthorizationTest {

    private static final Logger logger = LoggerFactory.getLogger(OpaResidualAuthorizationTest.class);

    private static final String JWT_ISSUER = "https://tenant.example.com";

    private static final String POLICY_PATH = "rego/policy.rego";
    private static final String POLICY_ID = "appserver";
    private static final String POLICY_PACKAGE = "contentgrid.appserver";

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

    @BeforeAll
    static void pushPolicyToOpa() throws Exception {
        String policy;
        try (var stream = Objects.requireNonNull(
                OpaResidualAuthorizationTest.class.getClassLoader().getResourceAsStream(POLICY_PATH),
                POLICY_PATH + " is missing from the test classpath")) {
            policy = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${system.policy.package}", POLICY_PACKAGE);
        }

        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://%s:%d/v1/policies/%s"
                            .formatted(opa.getHost(), opa.getMappedPort(8181), POLICY_ID)))
                    .PUT(BodyPublishers.ofString(policy))
                    .build();
            var response = client.send(request, BodyHandlers.ofString());
            assertThat(response.statusCode())
                    .as("loading the policy into OPA should succeed, but got: %s", response.body())
                    .isEqualTo(200);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoicingApi invoicingApi;

    @AfterEach
    void cleanupTestData() {
        invoicingApi.deleteAll();
    }

    private JwtRequestPostProcessor jwtRequestProcessor(String subject) {
        return SecurityMockMvcRequestPostProcessors.jwt()
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
                        .with(jwtRequestProcessor("creator"))
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

        mockMvc.perform(get("/customers").with(jwtRequestProcessor("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total_items_exact").value(1))
                .andExpect(jsonPath("$._embedded.['item'].length()").value(1))
                .andExpect(jsonPath("$._embedded.['item'][0].vat").value(matchingVat));
    }

    @Test
    void getCustomerById_matchingResidualPredicate_returnsHttp200() throws Exception {
        var matchingId = createCustomer("BE0000000003", 100);

        mockMvc.perform(get("/customers/{id}", matchingId).with(jwtRequestProcessor("reader")))
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

        mockMvc.perform(get("/customers/{id}", nonMatchingId).with(jwtRequestProcessor("reader")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCustomers_withoutAuthentication_isRejected() throws Exception {
        createCustomer("BE0000000005", 50);

        mockMvc.perform(get("/customers"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class NoOpJwtDecoderConfiguration {

        /**
         * Only needs to exist so {@link com.contentgrid.appserver.autoconfigure.security.DefaultSecurityAutoConfiguration#securityFilterChain}'s
         * {@code ObjectProvider<JwtDecoder.class>} is satisfied.
         */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("decode() should never be called in this test");
            };
        }
    }
}
