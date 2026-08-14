package com.contentgrid.appserver.integration.test.application.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import org.junit.jupiter.api.Test;

/**
 * The APP_SERVER_VERSION only works when application-model is a dependency, hence why this test isn't in the
 * application-model subproject.
 */
class AppserverVersionTest {

    @Test
    void applicationPackageHasImplementationVersion() {
        assertThat(Application.APP_SERVER_VERSION)
                .isNotNull()
                .matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
    }
}
