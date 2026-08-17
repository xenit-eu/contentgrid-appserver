package com.contentgrid.appserver.contentstore.impl.s3;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.contentgrid.appserver.autoconfigure.s3.testing.S3TestClients;
import com.contentgrid.appserver.contentstore.impl.utils.testing.S3MockUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Testcontainers
class S3MockContentStoreTest extends AbstractS3ContentStoreTest {

    @Container
    private static final S3MockContainer s3MockContainer = S3MockUtils.s3MockContainer();

    @Override
    protected S3AsyncClient createClient() {
        return S3TestClients.s3AsyncClient(s3MockContainer.getHttpEndpoint());
    }
}
