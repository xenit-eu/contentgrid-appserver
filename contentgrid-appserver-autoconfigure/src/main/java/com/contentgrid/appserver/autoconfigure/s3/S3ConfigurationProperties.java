package com.contentgrid.appserver.autoconfigure.s3;

import java.net.URI;

public interface S3ConfigurationProperties {
    URI endpoint();
    String accessKey();
    String secretKey();
    String region();
    boolean pathStyleAccess();
    int connectionPoolSize();
    int connectionPoolKeepAliveSeconds();
}
