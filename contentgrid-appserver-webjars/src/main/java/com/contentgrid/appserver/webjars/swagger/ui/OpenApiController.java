package com.contentgrid.appserver.webjars.swagger.ui;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenApiController {

    private static final Path PATH = Path.of("META-INF", "resources", "openapi.yml");

    private final Resource resource;

    public OpenApiController(Artifact artifact) throws ArtifactException {
        this.resource = artifact.load(PATH)
                .map(ArtifactEntryResource::new)
                .orElse(null);
    }


    @GetMapping("/openapi.yml")
    ResponseEntity<Resource> getOpenApiSpec() throws IOException {
        if (resource == null) {
            throw new FileNotFoundException("openapi.yml is not present");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_YAML)
                .body(resource);
    }

    @RequiredArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    private static class ArtifactEntryResource extends AbstractResource {

        private final ArtifactEntry artifactEntry;

        @Override
        public String getDescription() {
            return artifactEntry.getEntryReference().toString();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                return artifactEntry.getInputStream();
            } catch (ArtifactEntryUnreadableException e) {
                throw new IOException(e);
            }
        }
    }

}
