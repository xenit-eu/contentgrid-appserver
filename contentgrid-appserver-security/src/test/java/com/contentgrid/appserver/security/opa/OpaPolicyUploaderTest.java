package com.contentgrid.appserver.security.opa;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;

class OpaPolicyUploaderTest {

    private static final BlueprintArtifactReference TEST_REFERENCE = BlueprintArtifactReference.of("classpath:.");
    private static final BlueprintArtifactItemReference TEST_ITEM_REFERENCE =
            BlueprintArtifactItemReference.of(TEST_REFERENCE, "rego/policy.rego");

    // Small delays to keep these tests fast
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

        verify(opaClient).upsertPolicy("appserver", regoContent);
        InOrder inOrder = Mockito.inOrder(applicationContext);
        inOrder.verify(applicationContext).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == ReadinessState.REFUSING_TRAFFIC));
        inOrder.verify(applicationContext).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == ReadinessState.ACCEPTING_TRAFFIC));
    }

    @Test
    void regoFileAbsent_noOpaCall() throws Exception {
        when(blueprintArtifact.load(any())).thenReturn(Optional.empty());

        uploader.onApplicationEvent(event);

        verify(opaClient, never()).upsertPolicy(any(), any());
        verify(applicationContext, never()).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == ReadinessState.ACCEPTING_TRAFFIC));
        verify(applicationContext).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == LivenessState.BROKEN));
    }

    @Test
    void blueprintArtifactException_noOpaCallAndMarksLivenessBroken() throws Exception {
        when(blueprintArtifact.load(any())).thenThrow(
                new BlueprintArtifactException(TEST_REFERENCE, "cannot access artifact"));

        uploader.onApplicationEvent(event);

        verify(opaClient, never()).upsertPolicy(any(), any());
        verify(applicationContext, never()).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == ReadinessState.ACCEPTING_TRAFFIC));
        verify(applicationContext).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == LivenessState.BROKEN));
    }

    @Test
    void itemUnreadable_noOpaCallAndMarksLivenessBroken() throws Exception {
        var item = mock(BlueprintArtifactItem.class);
        when(blueprintArtifact.load(any())).thenReturn(Optional.of(item));
        when(item.getInputStream()).thenThrow(
                new BlueprintArtifactItemUnreadableException(TEST_ITEM_REFERENCE, "stream cannot be opened"));

        uploader.onApplicationEvent(event);

        verify(opaClient, never()).upsertPolicy(any(), any());
        verify(applicationContext, never()).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == ReadinessState.ACCEPTING_TRAFFIC));
        verify(applicationContext).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == LivenessState.BROKEN));
    }

    @Test
    void ioExceptionReadingStream_noOpaCallAndMarksLivenessBroken() throws Exception {
        var item = mock(BlueprintArtifactItem.class);
        var brokenStream = mock(InputStream.class);
        when(blueprintArtifact.load(any())).thenReturn(Optional.of(item));
        when(item.getInputStream()).thenReturn(brokenStream);
        when(brokenStream.readAllBytes()).thenThrow(new IOException("disk error"));

        uploader.onApplicationEvent(event);

        verify(opaClient, never()).upsertPolicy(any(), any());
        verify(applicationContext, never()).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == ReadinessState.ACCEPTING_TRAFFIC));
        verify(applicationContext).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == LivenessState.BROKEN));
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

        verify(opaClient, times(6)).upsertPolicy(any(), any());
        verify(applicationContext, never()).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == ReadinessState.ACCEPTING_TRAFFIC));
        verify(applicationContext).publishEvent(argThat(
                (AvailabilityChangeEvent<?> e) -> e.getState() == LivenessState.BROKEN));
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

        verify(opaClient).upsertPolicy("appserver", "package contentgrid.appserver\n");
    }
}
