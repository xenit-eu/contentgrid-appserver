package com.contentgrid.appserver.test.testcontainers;

import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ImageNameSubstitutor;

public class ContainerImageNameSubstitutor extends ImageNameSubstitutor {

    @Override
    public DockerImageName apply(DockerImageName originalImageName) {
        if (originalImageName.getRepository().contains("postgres")) {
            return DockerImageName.parse("paradedb/paradedb:latest");
        } else {
            return originalImageName;
        }
    }

    @Override
    protected String getDescription() {
        return "Substituting vanilla postgres with paradeDb";
    }
}
