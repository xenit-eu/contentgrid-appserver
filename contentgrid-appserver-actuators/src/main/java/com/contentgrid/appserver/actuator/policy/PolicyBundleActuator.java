package com.contentgrid.appserver.actuator.policy;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import java.io.IOException;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

/**
 * Serves the rego policy as an OPA bundle, so an OPA instance can pull it through its bundle plugin.
 * <p>
 * The bundle is assembled on every request. OPA polls continuously, but {@link PolicyBundleEtagFilter} answers
 * a repeat poll ahead of this endpoint, so in practice the bundle is built once per application start.
 * <p>
 * Actuator endpoints cannot read or write http headers, so the {@code ETag} of the bundle and the conditional
 * requests OPA makes with it are handled by {@link PolicyBundleEtagFilter} instead.
 */
@WebEndpoint(id = PolicyBundleActuator.ENDPOINT_ID)
public class PolicyBundleActuator {

    public static final String ENDPOINT_ID = "policybundle";

    /**
     * The rego package to use when the application is not deployed alongside a centralized OPA,
     * matching the package that OPA is queried with when the policy is loaded directly into it.
     */
    public static final String DEFAULT_POLICY_PACKAGE = "contentgrid.appserver";

    private static final MimeType CONTENT_TYPE = MimeType.valueOf("application/gzip");

    private final RegoPolicyLoader policyLoader;
    private final OpaBundleBuilder bundleBuilder = new OpaBundleBuilder();
    private final String policyPackage;

    public PolicyBundleActuator(BlueprintArtifact blueprintArtifact, String policyPackage) {
        this.policyLoader = new RegoPolicyLoader(blueprintArtifact);
        this.policyPackage = StringUtils.hasText(policyPackage) ? policyPackage : DEFAULT_POLICY_PACKAGE;
    }

    @ReadOperation(produces = "application/gzip")
    public WebEndpointResponse<Resource> readBundle()
            throws IOException, BlueprintArtifactException, BlueprintArtifactItemUnreadableException {
        var policy = policyLoader.readPolicy(policyPackage);
        var content = bundleBuilder.build(policy, policyPackage);
        return new WebEndpointResponse<>(new ByteArrayResource(content), WebEndpointResponse.STATUS_OK, CONTENT_TYPE);
    }
}
