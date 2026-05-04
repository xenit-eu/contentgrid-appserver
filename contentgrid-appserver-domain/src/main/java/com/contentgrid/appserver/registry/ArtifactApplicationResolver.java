package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.json.ApplicationSchemaConverter;
import com.contentgrid.appserver.application.model.json.DefaultApplicationSchemaConverter;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class ArtifactApplicationResolver implements ApplicationResolver {

    private static final Path PATH = Path.of("application-model.json");

    private final Artifact artifact;
    private final ApplicationSchemaConverter converter = new DefaultApplicationSchemaConverter();

    @Override
    @SneakyThrows
    public Optional<Application> resolve(ApplicationName name) {
        var artifactEntry = artifact.loadRequired(PATH);
        var application = converter.convert(artifactEntry.getInputStream());
        if (name.equals(application.getName())) {
            return Optional.of(application);
        } else {
            return Optional.empty();
        }
    }
}
