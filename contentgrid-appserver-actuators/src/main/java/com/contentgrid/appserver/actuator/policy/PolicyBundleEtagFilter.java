package com.contentgrid.appserver.actuator.policy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds the entity tag of the OPA bundle to the response, and answers conditional requests that already carry it
 * with {@code 304 Not Modified}.
 * <p>
 * The OPA bundle plugin polls continuously, but the policy comes from the blueprint artifact and is therefore fixed
 * for the lifetime of the application. The entity tag is a value picked once at startup rather than a hash over the
 * bundle, so this filter doesn't need the bundle.
 *
 * @see <a href="https://www.openpolicyagent.org/docs/management-bundles">OPA bundle service API</a>
 */
@Slf4j
public class PolicyBundleEtagFilter extends OncePerRequestFilter {

    private final String etag = UUID.randomUUID().toString();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (new ServletWebRequest(request, response).checkNotModified(etag)) {
            log.debug("Policy bundle is unchanged, answering with 304 Not Modified");
            return;
        }

        log.info("Policy bundle requested without a matching entity tag, serving it in full");
        filterChain.doFilter(request, response);
    }
}
