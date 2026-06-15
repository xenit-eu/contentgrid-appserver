package com.contentgrid.appserver.contentstore.impl.utils.testing;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import java.io.IOException;
import java.util.Properties;
import lombok.experimental.UtilityClass;

/**
 * Tag used in {@link S3MockContainer} must match the version of the com.adobe.testing:s3mock-testcontainers.
 */
@UtilityClass
public class S3MockUtils {

    private static final String S3_MOCK_VERSION;

    static {
        // Read the version of com.adobe.testing:s3mock-testcontainers by looking at pom.properties
        // of the published Maven package. When Renovate updates the version in build.gradle,
        // the tests will automatically use the new version.
        var props = new Properties();
        try (var is = S3MockContainer.class.getResourceAsStream(
                "/META-INF/maven/com.adobe.testing/s3mock-testcontainers/pom.properties")) {
            props.load(is);
            S3_MOCK_VERSION = props.getProperty("version");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static S3MockContainer s3MockContainer() {
        return new S3MockContainer(S3_MOCK_VERSION);
    }
}
