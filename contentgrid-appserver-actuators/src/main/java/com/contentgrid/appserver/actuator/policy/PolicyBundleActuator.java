package com.contentgrid.appserver.actuator.policy;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;

/**
 * Serves the rego policy as an OPA bundle, so an OPA instance can pull it through its bundle plugin.
 * <p>
 * Actuator endpoints cannot read or write http headers, so the {@code ETag} of the bundle and the conditional
 * requests OPA makes with it are handled by {@link PolicyBundleEtagFilter} instead.
 */
@WebEndpoint(id = PolicyBundleActuator.ENDPOINT_ID)
@RequiredArgsConstructor
public class PolicyBundleActuator {

    public static final String ENDPOINT_ID = "policybundle";

    private static final MimeType CONTENT_TYPE = MimeType.valueOf("application/gzip");

    private final PolicyBundleSource policyBundleSource;

    @ReadOperation(produces = "application/gzip")
    public WebEndpointResponse<Resource> readBundle()
            throws IOException, BlueprintArtifactException, BlueprintArtifactItemUnreadableException {
        var bundle = policyBundleSource.load();
        return new WebEndpointResponse<>(new ByteArrayResource(bundle.content()), WebEndpointResponse.STATUS_OK,
                CONTENT_TYPE);
    }
}
