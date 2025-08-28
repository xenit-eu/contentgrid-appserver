package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.rest.assembler.RootRepresentationModelAssembler;
import com.contentgrid.appserver.rest.assembler.EmptyRepresentationModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootRestController {

    private final RootRepresentationModelAssembler assembler = new RootRepresentationModelAssembler();

    @GetMapping("/")
    public EmptyRepresentationModel getRoot(Application application) {
        return assembler.toModel(application);
    }

}
