package com.contentgrid.appserver.actuator.policy;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PolicyBundleEtagFilterTest {

    private PolicyBundleEtagFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PolicyBundleEtagFilter();
    }

    @Test
    void firstRequestGetsAnEtagAndReachesTheEndpoint() throws ServletException, IOException {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request(null), response, chain);

        assertThat(response.getHeader(HttpHeaders.ETAG))
                .isNotNull()
                .startsWith("\"")
                .endsWith("\"");
        assertThat(chain.getRequest()).as("the endpoint should have been reached").isNotNull();
    }

    @Test
    void repeatRequestIsNotModifiedAndNeverReachesTheEndpoint() throws Exception {
        var etag = etagOf(getResponse());
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request(etag), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_MODIFIED.value());
        assertThat(response.getContentLength()).isZero();
        assertThat(response.getHeader(HttpHeaders.ETAG)).isEqualTo(etag);
        assertThat(chain.getRequest()).as("the endpoint should not have been reached").isNull();
    }

    @Test
    void staleEtagReachesTheEndpointAgain() throws ServletException, IOException {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request("\"stale\""), response, chain);

        assertThat(chain.getRequest()).as("the endpoint should have been reached").isNotNull();
    }

    @Test
    void etagIsStableAcrossRequests() throws ServletException, IOException {
        assertThat(etagOf(getResponse())).isEqualTo(etagOf(getResponse()));
    }

    private MockHttpServletResponse getResponse() throws ServletException, IOException {
        var response = new MockHttpServletResponse();
        filter.doFilter(request(null), response, new MockFilterChain());
        return response;
    }

    private static String etagOf(MockHttpServletResponse response) {
        return response.getHeader(HttpHeaders.ETAG);
    }

    private static MockHttpServletRequest request(String ifNoneMatch) {
        var request = new MockHttpServletRequest("GET", "/actuator/policybundle");
        if (ifNoneMatch != null) {
            request.addHeader(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        }
        return request;
    }
}
