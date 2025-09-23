package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.rest.assembler.RootRepresentationModelAssembler;
import com.contentgrid.appserver.rest.assembler.EmptyRepresentationModel;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootRestController {

    private final RootRepresentationModelAssembler assembler = new RootRepresentationModelAssembler();

    @GetMapping("/")
    public EmptyRepresentationModel getRoot(Application application, LinkFactoryProvider linkFactoryProvider) {
        return assembler.withContext(linkFactoryProvider).toModel(application);
    }

}
