package com.contentgrid.appserver.security.opa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.contentgrid.opa.client.OpaClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class OpaPolicyUploadServiceTest {

    // Small delays to keep these tests fast
    private static final OpaPolicyUploadRetryProperties RETRY_PROPERTIES =
            new OpaPolicyUploadRetryProperties(Duration.ofMillis(1), Duration.ofMillis(10), 2);

    OpaClient opaClient = mock(OpaClient.class);
    OpaStatusImpl opaStatus = new OpaStatusImpl(Status.DOWN);
    OpaPolicyUploadService service = new OpaPolicyUploadService(opaClient, RETRY_PROPERTIES, opaStatus);

    @Test
    void upsertPolicy_fail_marksDown() throws InterruptedException {
        when(opaClient.upsertPolicy(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("OPA unavailable")));
        var thread = new Thread(() -> {
            service.upsertPolicy("package contentgrid.appserver\n");
        });

        thread.start();
        verify(opaClient, after(50).atLeast(1)).upsertPolicy(any(), any());
        thread.interrupt();
        thread.join(100);

        assertThat(opaStatus.getHealth().getStatus()).isEqualTo(Status.DOWN);
        assertThat(opaStatus.getHealth().getDetails()).containsExactlyInAnyOrderEntriesOf(Map.of("OPAPolicyUploadException", "java.lang.RuntimeException: OPA unavailable"));
    }

    @Test
    void upsertPolicy_success_marksUploaded() {
        when(opaClient.upsertPolicy(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service.upsertPolicy("package contentgrid.appserver\n");

        assertThat(opaStatus.getHealth().getStatus()).isEqualTo(Status.UP);
        verify(opaClient).upsertPolicy("appserver", "package contentgrid.appserver\n");
    }

    @Test
    void upsertPolicy_eventuallySucceeds_marksUploaded() {
        when(opaClient.upsertPolicy(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("OPA unavailable")))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("OPA unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.upsertPolicy("package contentgrid.appserver\n");

        assertThat(opaStatus.getHealth().getStatus()).isEqualTo(Status.UP);
        verify(opaClient, times(3)).upsertPolicy(any(), any());
    }
}
