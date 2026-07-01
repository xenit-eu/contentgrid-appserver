package com.contentgrid.appserver.autoconfigure.opa;

import com.contentgrid.appserver.actuator.policy.PolicyVariables;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import com.contentgrid.opa.client.OpaClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.StringUtils;
import org.springframework.util.SystemPropertyUtils;

@Slf4j
@RequiredArgsConstructor
public class OpaPolicyUploader implements ApplicationListener<ApplicationReadyEvent> {

    private static final Path PATH = Path.of("rego", "policy.rego");
    private static final String POLICY_ID = "appserver";
    // The sidecar hosts only this app's policy, so the per-app package uniqueness that
    // contentgrid.system.policyPackage provides for the shared centralized-OPA/Solon setup isn't needed here.
    private static final String DEFAULT_POLICY_PACKAGE = "contentgrid.appserver";

    private static final PropertyPlaceholderHelper PROPERTY_PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(
            SystemPropertyUtils.PLACEHOLDER_PREFIX,
            SystemPropertyUtils.PLACEHOLDER_SUFFIX
    );

    private final BlueprintArtifact blueprintArtifact;
    private final OpaClient opaClient;
    private final String policyPackage;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            log.info("Starting OPA policy upload!");
            var maybePolicyItem = blueprintArtifact.load(PATH);
            if (maybePolicyItem.isEmpty()) {
                log.warn("No rego policy found at {} in {}", PATH, blueprintArtifact.getReference());
                return;
            }
            String regoContent;
            try (InputStream stream = maybePolicyItem.get().getInputStream()) {
                regoContent = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            var resolvedPackage = StringUtils.hasText(policyPackage) ? policyPackage : DEFAULT_POLICY_PACKAGE;
            var policyVariables = PolicyVariables.builder().policyPackageName(resolvedPackage).build();
            regoContent = PROPERTY_PLACEHOLDER_HELPER.replacePlaceholders(regoContent, policyVariables);
            opaClient.upsertPolicy(POLICY_ID, regoContent)
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            log.error("Failed to upload policy '{}' to OPA", POLICY_ID, error);
                        } else {
                            log.info("Policy '{}' uploaded to OPA", POLICY_ID);
                        }
                    });
        } catch (BlueprintArtifactException | BlueprintArtifactItemUnreadableException | IOException e) {
            log.error("Failed to read policy from blueprint artifact", e);
        }
    }
}
