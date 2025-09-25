package com.contentgrid.appserver.autoconfigure.contentstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.contentstore.api.ContentStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class FilesystemContentStoreAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            // Use initializer to have default conversion service
            .withInitializer(applicationContext -> applicationContext.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withConfiguration(AutoConfigurations.of(FilesystemContentStoreAutoConfiguration.class));

    @Test
    void checkDefaults() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("ephemeralContentStore");
                });
    }

    @Test
    void checkEphemeral() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.content-store.type=ephemeral")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("ephemeralContentStore");
                });
    }

    @Test
    void checkUnknown() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.content-store.type=unknown")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ContentStore.class);
                });
    }

    @Test
    void checkFileSystem(@TempDir Path path) {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=fs",
                        "contentgrid.appserver.content.fs.path=" + path
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("filesystemContentStore");
                });
    }

    @Test
    void checkFileSystem_nonExistentDirectory() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=fs",
                        "contentgrid.appserver.content.fs.path=classpath:non-existent"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkFileSystem_regularFile() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.content-store.type=fs",
                        "contentgrid.appserver.content.fs.path=classpath:test.json" // json file instead of directory
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkFileSystem_missingDirectory() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.content-store.type=fs")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

}