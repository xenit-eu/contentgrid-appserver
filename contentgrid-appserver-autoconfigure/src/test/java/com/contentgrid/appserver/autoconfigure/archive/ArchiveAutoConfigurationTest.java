package com.contentgrid.appserver.autoconfigure.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ArchiveAutoConfigurationTest {

    @TempDir
    static Path tempDir;

    static Path archiveZip;

    @BeforeAll
    static void createTestArchive() throws IOException {
        archiveZip = tempDir.resolve("test-archive.zip");
        try (var zos = new ZipOutputStream(Files.newOutputStream(archiveZip))) {
            zos.putNextEntry(new ZipEntry("hello.txt"));
            zos.write("hello from archive".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("subdir/nested.txt"));
            zos.write("nested file".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withConfiguration(AutoConfigurations.of(ArchiveAutoConfiguration.class));

    @Test
    void withoutProperty_contextStarts() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ArchiveExtractionBeanFactoryPostProcessor.class);
        });
    }

    @Test
    void withValidArchive_contextStarts() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.archive=" + archiveZip.toUri())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ArchiveExtractionBeanFactoryPostProcessor.class);
                    assertThat(context.getResource("archive:hello.txt")
                            .getContentAsString(StandardCharsets.UTF_8)).isEqualTo("hello from archive");
                    assertThat(context.getResource("archive:subdir/nested.txt")
                            .getContentAsString(StandardCharsets.UTF_8)).isEqualTo("nested file");
                });
    }

    @Test
    void withNonExistentArchive_contextFails() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.archive=file:/nonexistent/archive.zip")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }
}
