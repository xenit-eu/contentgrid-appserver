package com.contentgrid.appserver.rest.root;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.rest.root.assembler.RootRepresentationModel;
import com.contentgrid.appserver.rest.root.assembler.RootRepresentationModelAssembler;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootRestController {

    private final RootRepresentationModelAssembler assembler = new RootRepresentationModelAssembler();

    @GetMapping("/")
    public RootRepresentationModel getRoot(Application application, LinkFactoryProvider linkFactoryProvider) {
        return assembler.withContext(linkFactoryProvider).toModel(application);
    }

}
