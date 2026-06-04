package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.json.ApplicationSchemaConverter;
import com.contentgrid.appserver.application.model.json.DefaultApplicationSchemaConverter;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifact;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class BlueprintArtifactApplicationResolver implements ApplicationResolver {

    private static final Path PATH = Path.of("application-model.json");

    private final BlueprintArtifact blueprintArtifact;
    private final ApplicationSchemaConverter converter = new DefaultApplicationSchemaConverter();

    @Override
    @SneakyThrows
    public Application resolve(ApplicationName name) {
        var blueprintArtifactItem = blueprintArtifact.loadRequired(PATH);
        return converter.convert(blueprintArtifactItem.getInputStream());
    }
}
