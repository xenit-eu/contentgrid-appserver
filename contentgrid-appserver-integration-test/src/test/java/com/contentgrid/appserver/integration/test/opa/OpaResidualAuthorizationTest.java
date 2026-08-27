package com.contentgrid.appserver.integration.test.opa;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApi;
import com.contentgrid.appserver.integration.test.fixture.invoicing.InvoicingApiApplication;
import com.contentgrid.appserver.security.authority.GatewayAuthClaimNames;
import com.contentgrid.appserver.security.authority.GatewayJwtAuthenticationDetailsConverter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "contentgrid.events.rabbitmq.enabled=false",
        "contentgrid.thunx.abac.source=opa",
        "opa.query=data.contentgrid.appserver.allow == true",
        "management.endpoints.web.exposure.include=*",
})
@ContextConfiguration(classes = {InvoicingApiApplication.class, OpaResidualAuthorizationTest.NoOpJwtDecoderConfiguration.class})
@AutoConfigureMockMvc(printOnlyOnFailure = false)
class OpaResidualAuthorizationTest {

    private static final Logger logger = LoggerFactory.getLogger(OpaResidualAuthorizationTest.class);

    private static final String JWT_ISSUER = "https://tenant.example.com";

    /**
     * Fixed so it can be handed to OPA before the application binds it. OPA retries its bundle poll, so it
     * tolerates the port not listening yet when the container starts.
     */
    private static final int MANAGEMENT_PORT = freePort();

    static {
        // Makes the host's management port reachable from inside the OPA container as host.testcontainers.internal.
        org.testcontainers.Testcontainers.exposeHostPorts(MANAGEMENT_PORT);
    }

    @Container
    static GenericContainer<?> opa = new GenericContainer<>("openpolicyagent/opa:1.15.2-static")
            .withCopyToContainer(Transferable.of("""
                    services:
                      - name: appserver
                        url: http://host.testcontainers.internal:%d/actuator
                    bundles:
                      contentgrid:
                        service: appserver
                        resource: policybundle
                        polling:
                          min_delay_seconds: 1
                          max_delay_seconds: 10
                    """.formatted(MANAGEMENT_PORT)), "/config.yaml")
            .withCommand("run", "--server", "--log-format=json-pretty", "--set=decision_logs.console=true",
                    "--config-file=/config.yaml", "--addr", "0.0.0.0:8181")
            .withExposedPorts(8181)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            // Only wait for health, not bundles since bundles requires the appserver application
            .waitingFor(Wait.forHttp("/health").forPort(8181));

    @DynamicPropertySource
    static void opaProperties(DynamicPropertyRegistry registry) {
        registry.add("opa.service.url",
                () -> "http://%s:%d".formatted(opa.getHost(), opa.getMappedPort(8181)));
        registry.add("management.server.port", () -> MANAGEMENT_PORT);
    }

    /**
     * Waits until OPA reports every configured bundle as activated, which it can only do once it has pulled
     * the bundle from {@code /actuator/policybundle}. This cannot be the container's wait strategy: the
     * application only starts after the container does, so the container would never become ready.
     * <p>
     * This is {@code @BeforeEach} rather than {@code @BeforeAll} because the spring context is created during
     * test instance post-processing, which runs after {@code @BeforeAll}. Only the first call actually waits.
     */
    @BeforeEach
    void awaitBundleActivation() {
        try (var client = HttpClient.newHttpClient()) {
            Awaitility.await("OPA to activate the bundle pulled from the policy bundle endpoint")
                    .atMost(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(800))
                    .until(() -> bundlesAreActivated(client));
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

    private static boolean bundlesAreActivated(HttpClient client) {
        try {
            return client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://%s:%d%s".formatted(opa.getHost(), opa.getMappedPort(8181),
                            "/health?bundles=true")))
                    .build(), BodyHandlers.discarding()).statusCode() == 200;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (IOException e) {
            return false;
        }
    }

    private static int freePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("no free port for the management server", e);
        }
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
