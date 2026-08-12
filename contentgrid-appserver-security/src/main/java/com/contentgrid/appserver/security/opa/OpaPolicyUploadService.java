package com.contentgrid.appserver.security.opa;

import com.contentgrid.opa.client.OpaClient;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.scheduling.annotation.Async;

/**
 * Talks to the OPA sidecar to upload the app's rego policy. Retries indefinitely with backoff until it succeeds.
 * Runs async, so a slow or unreachable OPA never blocks application startup.
 */
@Slf4j
@RequiredArgsConstructor
public class OpaPolicyUploadService {

    private static final String POLICY_ID = "appserver";
    private static final String STATUS_FAILURE_KEY = "OPAPolicyUploadException";

    private final OpaClient opaClient;
    private final OpaPolicyUploadRetryProperties retryProperties;
    private final OpaHealthIndicator opaHealthIndicator;

    @Async("opaRetryExecutor")
    public void upsertPolicy(String regoContent) {
        var retryPolicy = RetryPolicy.builder()
                .includes(ExecutionException.class)
                .maxRetries(Long.MAX_VALUE)
                .delay(retryProperties.initialDelay())
                .multiplier(retryProperties.multiplier())
                .maxDelay(retryProperties.maxDelay())
                .build();
        var retryTemplate = new RetryTemplate(retryPolicy);
        retryTemplate.setRetryListener(new RetryListener() {
            @Override
            public void onRetryFailure(RetryPolicy policy, Retryable<?> retryable, Throwable throwable) {
                opaHealthIndicator.setDown(STATUS_FAILURE_KEY, throwable.getCause().toString());
                log.error("Failed to upload policy '{}' to OPA, retrying", POLICY_ID, throwable.getCause());
            }
        });

        try {
            retryTemplate.execute(() -> {
                opaClient.upsertPolicy(POLICY_ID, regoContent).get();
                return null;
            });
            opaHealthIndicator.setUp();
            log.info("Policy '{}' uploaded to OPA", POLICY_ID);
        } catch (RetryException e) {
            // maxRetries is unbounded, so the only way this executes is on interruption during backoff.
            Thread.currentThread().interrupt();
            log.warn("Interrupted while uploading policy '{}' to OPA", POLICY_ID, e.getCause());
        }
    }
}
