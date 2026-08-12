package com.contentgrid.appserver.integration.test.version;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import org.junit.jupiter.api.Test;

/**
 * The test has to be in this subproject, because the application-model project can't access its own jar with the
 * MANIFEST.MF.
 */
class AppserverVersionManifestTest {

    @Test
    void applicationPackageHasImplementationVersion() {
        assertThat(Application.class.getPackage().getImplementationVersion())
                .isNotNull()
                .matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
    }
}
