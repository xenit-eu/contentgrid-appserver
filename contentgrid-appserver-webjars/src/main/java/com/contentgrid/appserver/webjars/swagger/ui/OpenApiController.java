package com.contentgrid.appserver.webjars.swagger.ui;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class OpenApiController {

    private final ArtifactEntry artifactEntry;


    @GetMapping("/openapi.yml")
    ResponseEntity<Resource> getOpenApiSpec() throws IOException {
        if (artifactEntry == null) {
            throw new FileNotFoundException("openapi.yml is not present");
        }
        var resource = new ArtifactEntryResource(artifactEntry);
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
