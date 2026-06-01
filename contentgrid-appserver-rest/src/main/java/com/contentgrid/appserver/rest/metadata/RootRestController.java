package com.contentgrid.appserver.rest.metadata;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.openapi.OpenApiSpecConverter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiSpec;
import com.contentgrid.appserver.rest.metadata.assembler.RootRepresentationModel;
import com.contentgrid.appserver.rest.metadata.assembler.RootRepresentationModelAssembler;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootRestController {

    private final RootRepresentationModelAssembler assembler = new RootRepresentationModelAssembler();

    @GetMapping("/")
    public RootRepresentationModel getRoot(Application application, LinkFactoryProvider linkFactoryProvider) {
        return assembler.withContext(linkFactoryProvider).toModel(application);
    }

    @GetMapping(value = "/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OpenApiSpec> openApiSpecJson(Application application) {
        return ResponseEntity.ok(OpenApiSpecConverter.convert(application));
    }

    @GetMapping(value = "/openapi.yml", produces = MediaType.APPLICATION_YAML_VALUE)
    public ResponseEntity<OpenApiSpec> openApiSpecYml(Application application) {
        return ResponseEntity.ok(OpenApiSpecConverter.convert(application));
    }

}
