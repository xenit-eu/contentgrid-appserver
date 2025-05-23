package com.contentgrid.appserver.rest.assembler;

import com.contentgrid.appserver.application.model.Application;

public interface EntityDataRepresentationModelAssemblerProvider {
    EntityDataRepresentationModelAssembler getAssemblerFor(Application application);
}
