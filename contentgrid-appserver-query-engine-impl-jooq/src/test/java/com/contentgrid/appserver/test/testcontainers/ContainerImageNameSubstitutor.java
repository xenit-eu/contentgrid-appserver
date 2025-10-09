package com.contentgrid.appserver.test.testcontainers;

import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ImageNameSubstitutor;

public class ContainerImageNameSubstitutor extends ImageNameSubstitutor {

    @Override
    public DockerImageName apply(DockerImageName originalImageName) {
        if (originalImageName.getRepository().contains("postgres")) {
            return DockerImageName.parse("contentgrid/contentgrid-paradedb:local");
        } else {
            return originalImageName;
        }
    }

    @Override
    protected String getDescription() {
        return "Substituting DB images with a customized ParadeDB version.";
    }
}