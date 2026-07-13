package com.contentgrid.appserver.autoconfigure.opa;

import com.contentgrid.appserver.actuator.policy.PolicyVariables;
import com.contentgrid.appserver.autoconfigure.opa.OpaPolicyUploaderAutoConfiguration.OpaPolicyUploadRetryProperties;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import com.contentgrid.opa.client.OpaClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.StringUtils;
import org.springframework.util.SystemPropertyUtils;

/**
 * Uploads the app's Rego policy to the OPA sidecar on startup.
 * <p>
 * Reaching OPA with the policy is a hard requirement: an OPA queried for a policy it never received returns
 * an undefined decision (HTTP 200, no {@code result} field, no indication that no policy was ever loaded).
 * So {@link ReadinessState#REFUSING_TRAFFIC} is published as the very first thing this listener does - before
 * the policy is even read - so the pod's readiness probe ({@code /actuator/health/readiness}) stays DOWN and
 * no traffic is routed to it until a policy is confirmed uploaded.
 * <p>
 * The OPA sidecar may still be booting when {@link ApplicationReadyEvent} fires, so upload failures are
 * retried with capped exponential backoff; once the upload succeeds, {@link ReadinessState#ACCEPTING_TRAFFIC}
 * is published. When retries are exhausted or there are issues with the policy file,
 * {@link LivenessState#BROKEN} is published instead.
 */
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
    private final OpaPolicyUploadRetryProperties retryProperties;

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        if (StringUtils.hasText(policyPackage)) {
            log.warn("Skipping OPA policy upload because contentgrid.system.policyPackage ('{}') is set; this "
                    + "deployment is expected to expose its policy via the /actuator/policy endpoint for "
                    + "centralized pickup instead of pushing it directly to the OPA at opa.service.url.",
                    policyPackage);
            return;
        }
        var eventPublisher = event.getApplicationContext();
        log.info("Starting OPA policy upload!");
        AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.REFUSING_TRAFFIC);
        try {
            var maybePolicyItem = blueprintArtifact.load(PATH);
            if (maybePolicyItem.isEmpty()) {
                log.error("No rego policy found at {} in {}", PATH, blueprintArtifact.getReference());
                AvailabilityChangeEvent.publish(eventPublisher, this, LivenessState.BROKEN);
                return;
            }
            String regoContent;
            try (InputStream stream = maybePolicyItem.get().getInputStream()) {
                regoContent = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            var policyVariables = PolicyVariables.builder().policyPackageName(DEFAULT_POLICY_PACKAGE).build();
            regoContent = PROPERTY_PLACEHOLDER_HELPER.replacePlaceholders(regoContent, policyVariables);
            uploadWithRetry(event.getApplicationContext(), regoContent);
        } catch (BlueprintArtifactException | BlueprintArtifactItemUnreadableException | IOException e) {
            log.error("Failed to read policy from blueprint artifact", e);
            AvailabilityChangeEvent.publish(eventPublisher, this, LivenessState.BROKEN);
        }
    }

    /**
     * Blocks (retrying with backoff) until the policy is uploaded or retries are exhausted. This intentionally
     * blocks the {@link ApplicationReadyEvent} listener thread: Spring Boot only publishes
     * {@link ReadinessState#ACCEPTING_TRAFFIC} itself once all {@code ApplicationReadyEvent} listeners have
     * returned, so blocking here is what keeps the pod out of the readiness traffic pool until the upload
     * either succeeds or is given up on.
     */
    private void uploadWithRetry(ApplicationEventPublisher eventPublisher, String regoContent) {
        var retryPolicy = RetryPolicy.builder()
                .includes(ExecutionException.class)
                .maxRetries(retryProperties.maxRetries())
                .delay(retryProperties.initialDelay())
                .multiplier(retryProperties.multiplier())
                .maxDelay(retryProperties.maxDelay())
                .build();
        var retryTemplate = new RetryTemplate(retryPolicy);
        retryTemplate.setRetryListener(new RetryListener() {
            @Override
            public void onRetryFailure(RetryPolicy policy, Retryable<?> retryable, Throwable throwable) {
                log.error("Failed to upload policy '{}' to OPA, retrying", POLICY_ID, throwable.getCause());
            }
        });

        try {
            retryTemplate.execute(() -> {
                opaClient.upsertPolicy(POLICY_ID, regoContent).get();
                return null;
            });
            log.info("Policy '{}' uploaded to OPA", POLICY_ID);
            AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.ACCEPTING_TRAFFIC);
        } catch (RetryException e) {
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while uploading policy '{}' to OPA; pod remains out of the readiness "
                        + "traffic pool", POLICY_ID, e.getCause());
                return;
            }
            log.error("Giving up uploading policy '{}' to OPA after {} retries; marking liveness as broken so "
                    + "the pod is restarted", POLICY_ID, retryProperties.maxRetries(), e.getCause());
            AvailabilityChangeEvent.publish(eventPublisher, this, LivenessState.BROKEN);
        }
    }
}
