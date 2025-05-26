package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.rest.assembler.RootRepresentationModelAssembler;
import com.contentgrid.appserver.rest.assembler.processor.ContextRepresentationModelProcessor;
import com.contentgrid.appserver.rest.assembler.RootRepresentationModel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class RootRestController {

    private final RootRepresentationModelAssembler assembler = new RootRepresentationModelAssembler();

    @GetMapping("/")
    public RootRepresentationModel getRoot(Application application) {
        return assembler.toModel(application);
    }

}
