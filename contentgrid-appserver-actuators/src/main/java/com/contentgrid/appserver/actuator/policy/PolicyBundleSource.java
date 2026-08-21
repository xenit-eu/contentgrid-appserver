package com.contentgrid.appserver.actuator.policy;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * Assembles the OPA bundle from the rego policy and holds on to it.
 * <p>
 * The policy comes from the blueprint artifact, which is fixed for the lifetime of the application, so the bundle
 * is built on first use and reused afterwards. That keeps the entity tag stable, which lets
 * {@link PolicyBundleEtagFilter} answer repeat requests without rebuilding anything.
 */
@Slf4j
public class PolicyBundleSource {

    /**
     * The rego package to use when the application is not deployed alongside a centralized OPA,
     * matching the package that OPA is queried with when the policy is loaded directly into it.
     */
    public static final String DEFAULT_POLICY_PACKAGE = "contentgrid.appserver";

    private final RegoPolicyLoader policyLoader;
    private final OpaBundleBuilder bundleBuilder = new OpaBundleBuilder();
    private final String policyPackage;

    private final Object lock = new Object[0];

    // Using volatile is fine here, since the properties of the bundle object are never updated.
    // The object as a whole is set.
    @SuppressWarnings("java:S3077")
    private volatile PolicyBundle bundle;

    public PolicyBundleSource(BlueprintArtifact blueprintArtifact, String policyPackage) {
        this.policyLoader = new RegoPolicyLoader(blueprintArtifact);
        this.policyPackage = StringUtils.hasText(policyPackage) ? policyPackage : DEFAULT_POLICY_PACKAGE;
    }

    /**
     * Returns the bundle, building it on the first call.
     */
    public PolicyBundle load()
            throws IOException, BlueprintArtifactException, BlueprintArtifactItemUnreadableException {
        if (bundle == null) {
            synchronized (lock) {
                if (bundle == null) {
                    bundle = build();
                }
            }
        }
        return bundle;
    }

    private PolicyBundle build()
            throws IOException, BlueprintArtifactException, BlueprintArtifactItemUnreadableException {
        var policy = policyLoader.readPolicy(policyPackage);
        var content = bundleBuilder.build(policy, policyPackage);
        var etag = digest(content);
        log.info("Built OPA policy bundle for package '{}' ({} bytes, etag {})", policyPackage, content.length, etag);
        return new PolicyBundle(content, etag);
    }

    /**
     * Hashes the entire archive rather than the policy it holds, so that a change to the bundle layout
     * or to the manifest also produces a new entity tag. This does mean the archive has to be stable
     */
    private static String digest(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM is required to support SHA-256", e);
        }
    }
}
