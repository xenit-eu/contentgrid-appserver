package com.contentgrid.appserver.security.opa;

import com.contentgrid.appserver.actuator.policy.PolicyVariables;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.SystemPropertyUtils;

@Slf4j
@RequiredArgsConstructor
public class OpaPolicyUploadInitializer {

    private static final Path PATH = Path.of("rego", "policy.rego");
    private static final String DEFAULT_POLICY_PACKAGE = "contentgrid.appserver";

    private static final PropertyPlaceholderHelper PROPERTY_PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(
            SystemPropertyUtils.PLACEHOLDER_PREFIX,
            SystemPropertyUtils.PLACEHOLDER_SUFFIX
    );

    private final BlueprintArtifact blueprintArtifact;
    private final OpaPolicyUploadService opaPolicyUploadService;

    @PostConstruct
    public void startPolicyUpload() {
        String regoContent;
        try {
            var maybePolicyItem = blueprintArtifact.load(PATH);
            if (maybePolicyItem.isEmpty()) {
                log.error("No rego policy found at {} in {}", PATH, blueprintArtifact.getReference());
                return;
            }
            try (InputStream stream = maybePolicyItem.get().getInputStream()) {
                regoContent = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            var policyVariables = PolicyVariables.builder().policyPackageName(DEFAULT_POLICY_PACKAGE).build();
            regoContent = PROPERTY_PLACEHOLDER_HELPER.replacePlaceholders(regoContent, policyVariables);
        } catch (BlueprintArtifactException | BlueprintArtifactItemUnreadableException | IOException e) {
            log.error("Failed to read policy from blueprint artifact", e);
            return;
        }

        // Call Async method so the postconstruct can finish and the bean can initialize
        opaPolicyUploadService.upsertPolicy(regoContent);
    }
}
