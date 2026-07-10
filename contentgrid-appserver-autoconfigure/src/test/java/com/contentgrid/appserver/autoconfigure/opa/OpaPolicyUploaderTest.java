package com.contentgrid.appserver.autoconfigure.opa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.contentgrid.appserver.autoconfigure.opa.OpaPolicyUploaderAutoConfiguration.OpaPolicyUploadRetryProperties;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItem;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemReference;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReference;
import com.contentgrid.opa.client.OpaClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.boot.availability.ApplicationAvailabilityBean;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.application.ReadinessStateHealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class OpaPolicyUploaderTest {

    private static final BlueprintArtifactReference TEST_REFERENCE = BlueprintArtifactReference.of("classpath:.");
    private static final BlueprintArtifactItemReference TEST_ITEM_REFERENCE =
            BlueprintArtifactItemReference.of(TEST_REFERENCE, "rego/policy.rego");

    // Small delays keep these tests fast; the actual defaults are asserted separately in the auto-configuration test.
    private static final OpaPolicyUploadRetryProperties RETRY_PROPERTIES =
            new OpaPolicyUploadRetryProperties(Duration.ofMillis(1), Duration.ofMillis(10), 2, 5);

    BlueprintArtifact blueprintArtifact = mock(BlueprintArtifact.class);
    OpaClient opaClient = mock(OpaClient.class);
    ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);
    ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
    OpaPolicyUploader uploader = new OpaPolicyUploader(blueprintArtifact, opaClient, "", RETRY_PROPERTIES);

    @BeforeEach
    void setApplicationContext() {
        when(event.getApplicationContext()).thenReturn(applicationContext);
    }

    @Test
    void happyPath_uploadsRegoToOpa() throws Exception {
        var regoContent = "package contentgrid.appserver\n";
        var item = mock(BlueprintArtifactItem.class);
        when(blueprintArtifact.load(any())).thenReturn(Optional.of(item));
        when(item.getInputStream()).thenReturn(new ByteArrayInputStream(regoContent.getBytes(StandardCharsets.UTF_8)));
        when(opaClient.upsertPolicy(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        uploader.onApplicationEvent(event);

        verify(opaClient).upsertPolicy(eq("appserver"), eq(regoContent));
    }

    @Test
    void regoFileAbsent_noOpaCall() throws Exception {
        when(blueprintArtifact.load(any())).thenReturn(Optional.empty());
        when(blueprintArtifact.getReference()).thenReturn(TEST_REFERENCE);

        uploader.onApplicationEvent(event);

        verify(opaClient, never()).upsertPolicy(any(), any());
    }

    @Test
    void blueprintArtifactException_noOpaCall() throws Exception {
        when(blueprintArtifact.load(any())).thenThrow(
                new BlueprintArtifactException(TEST_REFERENCE, "cannot access artifact"));

        uploader.onApplicationEvent(event);

        verify(opaClient, never()).upsertPolicy(any(), any());
    }

    @Test
    void itemUnreadable_noOpaCall() throws Exception {
        var item = mock(BlueprintArtifactItem.class);
        when(blueprintArtifact.load(any())).thenReturn(Optional.of(item));
        when(item.getInputStream()).thenThrow(
                new BlueprintArtifactItemUnreadableException(TEST_ITEM_REFERENCE, "stream cannot be opened"));

        uploader.onApplicationEvent(event);

        verify(opaClient, never()).upsertPolicy(any(), any());
    }

    @Test
    void ioExceptionReadingStream_noOpaCall() throws Exception {
        var item = mock(BlueprintArtifactItem.class);
        var brokenStream = mock(InputStream.class);
        when(blueprintArtifact.load(any())).thenReturn(Optional.of(item));
        when(item.getInputStream()).thenReturn(brokenStream);
        when(brokenStream.readAllBytes()).thenThrow(new IOException("disk error"));

        uploader.onApplicationEvent(event);

        verify(opaClient, never()).upsertPolicy(any(), any());
    }

    @Test
    void opaClientAlwaysFails_marksLivenessBrokenWithoutPropagatingException() throws Exception {
        var regoContent = "package contentgrid.appserver\n";
        var item = mock(BlueprintArtifactItem.class);
        when(blueprintArtifact.load(any())).thenReturn(Optional.of(item));
        when(item.getInputStream()).thenReturn(new ByteArrayInputStream(regoContent.getBytes(StandardCharsets.UTF_8)));
        when(opaClient.upsertPolicy(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("OPA unavailable")));

        uploader.onApplicationEvent(event);

        verify(applicationContext, never()).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == ReadinessState.ACCEPTING_TRAFFIC));
        verify(applicationContext).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == LivenessState.BROKEN));
    }

    @Test
    void policyUpload_publishesReadinessRefusingTrafficThenAcceptingTraffic() throws Exception {
        var regoContent = "package contentgrid.appserver\n";
        var item = mock(BlueprintArtifactItem.class);
        when(blueprintArtifact.load(any())).thenReturn(Optional.of(item));
        when(item.getInputStream()).thenReturn(new ByteArrayInputStream(regoContent.getBytes(StandardCharsets.UTF_8)));
        when(opaClient.upsertPolicy(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        uploader.onApplicationEvent(event);

        InOrder inOrder = Mockito.inOrder(applicationContext);
        inOrder.verify(applicationContext).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == ReadinessState.REFUSING_TRAFFIC));
        inOrder.verify(applicationContext).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == ReadinessState.ACCEPTING_TRAFFIC));
    }

    @Test
    void policyUpload_actuallyGatesTheReadinessHealthIndicatorThatBacksActuatorHealthReadiness() throws Exception {
        // Uses real Spring Boot availability/health machinery (no mocking of AvailabilityChangeEvent or
        // ReadinessStateHealthIndicator) to prove that our AvailabilityChangeEvent publications actually
        // drive the same ReadinessStateHealthIndicator that backs /actuator/health/readiness.
        var regoContent = "package contentgrid.appserver\n";
        var item = mock(BlueprintArtifactItem.class);
        when(blueprintArtifact.load(any())).thenReturn(Optional.of(item));
        when(item.getInputStream()).thenReturn(new ByteArrayInputStream(regoContent.getBytes(StandardCharsets.UTF_8)));

        var uploadLatch = new CountDownLatch(1);
        when(opaClient.upsertPolicy(any(), any())).thenAnswer(invocation -> {
            uploadLatch.await();
            return CompletableFuture.completedFuture(null);
        });

        var realContext = new AnnotationConfigApplicationContext();
        var availability = new ApplicationAvailabilityBean();
        realContext.addApplicationListener(availability);
        realContext.refresh();
        var readinessIndicator = new ReadinessStateHealthIndicator(availability);

        when(event.getApplicationContext()).thenReturn(realContext);

        try {
            // Before any readiness event has ever been published, the app is not reporting ready.
            assertThat(readinessIndicator.health().getStatus()).isNotEqualTo(Status.UP);

            var uploadThread = new Thread(() -> uploader.onApplicationEvent(event));
            uploadThread.start();

            // Wait for the REFUSING_TRAFFIC signal published before the (currently latched) upload attempt.
            Awaitility.await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> availability.getState(ReadinessState.class) == ReadinessState.REFUSING_TRAFFIC);
            assertThat(readinessIndicator.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);

            uploadLatch.countDown();
            uploadThread.join(5000);

            assertThat(readinessIndicator.health().getStatus()).isEqualTo(Status.UP);
        } finally {
            realContext.close();
        }
    }

    @Test
    void regoFileAbsent_readinessUntouched() throws Exception {
        when(blueprintArtifact.load(any())).thenReturn(Optional.empty());
        when(blueprintArtifact.getReference()).thenReturn(TEST_REFERENCE);

        uploader.onApplicationEvent(event);

        // No policy to upload, so there is nothing to gate readiness on.
        verify(applicationContext, never()).publishEvent(any(AvailabilityChangeEvent.class));
    }

    @Test
    void policyPackageSet_skipsUploadEntirely() throws Exception {
        var uploaderWithPackage = new OpaPolicyUploader(blueprintArtifact, opaClient, "tenant.xyz", RETRY_PROPERTIES);

        uploaderWithPackage.onApplicationEvent(event);

        verify(opaClient, never()).upsertPolicy(any(), any());
        verify(blueprintArtifact, never()).load(any());
        verify(applicationContext, never()).publishEvent(any(AvailabilityChangeEvent.class));
    }

    @Test
    void placeholderInRego_emptyPolicyPackage_replacedWithStaticDefault() throws Exception {
        var regoContent = "package ${system.policy.package}\n";
        var item = mock(BlueprintArtifactItem.class);
        when(blueprintArtifact.load(any())).thenReturn(Optional.of(item));
        when(item.getInputStream()).thenReturn(new ByteArrayInputStream(regoContent.getBytes(StandardCharsets.UTF_8)));
        when(opaClient.upsertPolicy(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        uploader.onApplicationEvent(event);

        verify(opaClient).upsertPolicy(eq("appserver"), eq("package contentgrid.appserver\n"));
    }
}
