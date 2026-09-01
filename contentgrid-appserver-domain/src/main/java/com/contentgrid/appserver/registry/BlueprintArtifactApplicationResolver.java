package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.json.ApplicationSchemaConverter;
import com.contentgrid.appserver.application.model.json.DefaultApplicationSchemaConverter;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class BlueprintArtifactApplicationResolver implements ApplicationResolver {

    private static final Path PATH = Path.of("application-model.json");

    private final BlueprintArtifact blueprintArtifact;
    private static final ApplicationSchemaConverter CONVERTER = new DefaultApplicationSchemaConverter();

    @Override
    @SneakyThrows
    public Application resolve(ApplicationName name) {
        var started = System.nanoTime();
        var blueprintArtifactItem = blueprintArtifact.loadRequired(PATH);
        var loaded = System.nanoTime();
        var application = CONVERTER.convert(blueprintArtifactItem.getInputStream());
        log.info("Startup timing: loaded application-model item in {} ms and converted it in {} ms",
                (loaded - started) / 1_000_000, (System.nanoTime() - loaded) / 1_000_000);
        return application;
    }
}
