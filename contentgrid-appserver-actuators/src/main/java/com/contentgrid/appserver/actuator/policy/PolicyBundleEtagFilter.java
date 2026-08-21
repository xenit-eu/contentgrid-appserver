package com.contentgrid.appserver.actuator.policy;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds the entity tag of the OPA bundle to the response, and answers conditional requests that already carry it
 * with {@code 304 Not Modified}.
 * <p>
 * The OPA bundle plugin polls continuously, but the policy never changes while the application runs. Handling this
 * ahead of {@link PolicyBundleActuator} means a repeat poll neither transfers the bundle nor makes OPA recompile it.
 *
 * @see <a href="https://www.openpolicyagent.org/docs/management-bundles">OPA bundle service API</a>
 */
@Slf4j
@RequiredArgsConstructor
public class PolicyBundleEtagFilter extends OncePerRequestFilter {

    private final PolicyBundleSource policyBundleSource;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String etag;
        try {
            etag = policyBundleSource.load().etag();
        } catch (IOException | BlueprintArtifactException | BlueprintArtifactItemUnreadableException e) {
            log.debug("Could not determine the policy bundle entity tag, letting the endpoint report the failure", e);
            filterChain.doFilter(request, response);
            return;
        }

        // Writes the ETag header, and sets the 304 status when If-None-Match already carries this entity tag.
        if (new ServletWebRequest(request, response).checkNotModified(etag)) {
            log.debug("Policy bundle is unchanged, answering with 304 Not Modified");
            return;
        }

        log.debug("Policy bundle requested without a matching entity tag, serving it in full");
        filterChain.doFilter(request, response);
    }
}
