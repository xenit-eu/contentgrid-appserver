package com.contentgrid.appserver.autoconfigure.s3;

import java.net.URI;
import java.time.Duration;

public interface S3ConfigurationProperties {
    URI endpoint();
    String accessKey();
    String secretKey();
    String region();
    boolean pathStyleAccess();
    int connectionPoolSize();
    Duration connectionPoolIdleTimeout();
    Duration connectionTimeout();
    Duration readTimeout();
    Duration writeTimeout();
}
