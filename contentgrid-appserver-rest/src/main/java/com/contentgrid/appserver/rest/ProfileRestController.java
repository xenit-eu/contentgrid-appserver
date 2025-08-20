package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.rest.assembler.EmptyRepresentationModel;
import com.contentgrid.appserver.rest.assembler.ProfileRootRepresentationModelAssembler;
import com.contentgrid.appserver.rest.assembler.EntityProfileRepresentationModel;
import com.contentgrid.appserver.rest.assembler.EntityProfileRepresentationModelAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileRestController {

    private final ProfileRootRepresentationModelAssembler profileRootAssembler = new ProfileRootRepresentationModelAssembler();
    private final EntityProfileRepresentationModelAssembler profileEntityAssembler;

    @GetMapping
    public EmptyRepresentationModel getProfile(Application application) {
        return profileRootAssembler.toModel(application);
    }

    @GetMapping(value = "/{entityName}", produces = MediaTypes.HAL_FORMS_JSON_VALUE)
    public EntityProfileRepresentationModel getEntityProfile(
            Application application, @PathVariable PathSegmentName entityName
    ) {
        var entity = application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return profileEntityAssembler.withContext(application).toModel(entity);
    }
}
