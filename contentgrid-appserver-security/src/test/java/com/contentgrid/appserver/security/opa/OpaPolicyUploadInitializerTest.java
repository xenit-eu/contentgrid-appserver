package com.contentgrid.appserver.security.opa;

import static org.mockito.ArgumentMatchers.any;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpaPolicyUploadInitializerTest {

    private static final BlueprintArtifactReference TEST_REFERENCE = BlueprintArtifactReference.of("classpath:.");
    private static final BlueprintArtifactItemReference TEST_ITEM_REFERENCE =
            BlueprintArtifactItemReference.of(TEST_REFERENCE, "rego/policy.rego");

    BlueprintArtifact blueprintArtifact = mock(BlueprintArtifact.class);
    OpaPolicyUploadService opaPolicyUploadService = mock(OpaPolicyUploadService.class);
    OpaPolicyUploadInitializer initializer =
            new OpaPolicyUploadInitializer(blueprintArtifact, opaPolicyUploadService);

    static Stream<Arguments> happyPathScenarios() {
        return Stream.of(
                Arguments.argumentSet("content without placeholder is passed through unchanged",
                        "package contentgrid.appserver\n", "package contentgrid.appserver\n"),
                Arguments.argumentSet("empty policy package placeholder is replaced with the static default",
                        "package ${system.policy.package}\n", "package contentgrid.appserver\n")
        );
    }

    @ParameterizedTest
    @MethodSource("happyPathScenarios")
    void startsUpload(String regoContent, String expectedUpsertedContent) throws Exception {
        var item = mock(BlueprintArtifactItem.class);
        when(blueprintArtifact.load(any())).thenReturn(Optional.of(item));
        when(item.getInputStream()).thenReturn(new ByteArrayInputStream(regoContent.getBytes(StandardCharsets.UTF_8)));

        initializer.startPolicyUpload();

        verify(opaPolicyUploadService).upsertPolicy(expectedUpsertedContent);
    }

    static Stream<Arguments> noUpsertScenarios() {
        return Stream.of(
                Arguments.argumentSet("rego file absent",
                        (ThrowingConsumer<BlueprintArtifact>) artifact ->
                                when(artifact.load(any())).thenReturn(Optional.empty())),
                Arguments.argumentSet("blueprint artifact cannot be accessed",
                        (ThrowingConsumer<BlueprintArtifact>) artifact ->
                                when(artifact.load(any())).thenThrow(
                                        new BlueprintArtifactException(TEST_REFERENCE, "cannot access artifact"))),
                Arguments.argumentSet("policy item is unreadable",
                        (ThrowingConsumer<BlueprintArtifact>) artifact -> {
                            var item = mock(BlueprintArtifactItem.class);
                            when(artifact.load(any())).thenReturn(Optional.of(item));
                            when(item.getInputStream()).thenThrow(
                                    new BlueprintArtifactItemUnreadableException(TEST_ITEM_REFERENCE,
                                            "stream cannot be opened"));
                        }),
                Arguments.argumentSet("stream throws IOException while reading",
                        (ThrowingConsumer<BlueprintArtifact>) artifact -> {
                            var item = mock(BlueprintArtifactItem.class);
                            var brokenStream = mock(InputStream.class);
                            when(artifact.load(any())).thenReturn(Optional.of(item));
                            when(item.getInputStream()).thenReturn(brokenStream);
                            when(brokenStream.readAllBytes()).thenThrow(new IOException("disk error"));
                        })
        );
    }

    @ParameterizedTest
    @MethodSource("noUpsertScenarios")
    void noUpsertAttemptWhenPolicyCannotBeRead(ThrowingConsumer<BlueprintArtifact> setup) throws Throwable {
        setup.accept(blueprintArtifact);

        initializer.startPolicyUpload();

        verify(opaPolicyUploadService, never()).upsertPolicy(any());
    }
}
