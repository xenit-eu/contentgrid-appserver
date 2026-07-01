package com.contentgrid.appserver.autoconfigure.opa;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;

class OpaPolicyUploaderTest {

    private static final BlueprintArtifactReference TEST_REFERENCE = BlueprintArtifactReference.of("classpath:.");
    private static final BlueprintArtifactItemReference TEST_ITEM_REFERENCE =
            BlueprintArtifactItemReference.of(TEST_REFERENCE, "rego/policy.rego");

    BlueprintArtifact blueprintArtifact = mock(BlueprintArtifact.class);
    OpaClient opaClient = mock(OpaClient.class);
    ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
    OpaPolicyUploader uploader = new OpaPolicyUploader(blueprintArtifact, opaClient, "");

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
    void opaClientFails_noExceptionPropagated() throws Exception {
        var regoContent = "package contentgrid.appserver\n";
        var item = mock(BlueprintArtifactItem.class);
        when(blueprintArtifact.load(any())).thenReturn(Optional.of(item));
        when(item.getInputStream()).thenReturn(new ByteArrayInputStream(regoContent.getBytes(StandardCharsets.UTF_8)));
        when(opaClient.upsertPolicy(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("OPA unavailable")));

        uploader.onApplicationEvent(event);

        verify(opaClient).upsertPolicy(eq("appserver"), eq(regoContent));
    }

    @Test
    void placeholderInRego_replacedWithConfiguredPolicyPackage() throws Exception {
        var uploaderWithPackage = new OpaPolicyUploader(blueprintArtifact, opaClient, "tenant.xyz");
        var regoContent = "package ${system.policy.package}\n";
        var item = mock(BlueprintArtifactItem.class);
        when(blueprintArtifact.load(any())).thenReturn(Optional.of(item));
        when(item.getInputStream()).thenReturn(new ByteArrayInputStream(regoContent.getBytes(StandardCharsets.UTF_8)));
        when(opaClient.upsertPolicy(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        uploaderWithPackage.onApplicationEvent(event);

        verify(opaClient).upsertPolicy(eq("appserver"), eq("package tenant.xyz\n"));
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
