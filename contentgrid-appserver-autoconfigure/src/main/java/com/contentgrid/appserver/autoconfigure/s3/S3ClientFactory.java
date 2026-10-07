package com.contentgrid.appserver.autoconfigure.s3;

import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.multipart.MultipartConfiguration;

/**
 * Creates S3 clients from the appserver configuration properties.
 * <ul>
 *     <li>Path-style access is configurable: most S3-compatible stores only support path-style, while
 *     others (like AWS itself) prefer virtual-host style.</li>
 *     <li>Without a region, the region is set to {@code none}: S3-compatible stores don't require one, and
 *     a placeholder is less confusing than silently signing for a real AWS region.</li>
 *     <li>Checksums are only calculated/validated where the S3 API requires them, because S3-compatible
 *     stores often reject the CRC trailers with aws-chunked encoding that the SDK sends by default.</li>
 *     <li>The default profile file is replaced with an empty one, so the SDK never picks up AWS
 *     configuration or credentials from the user account running the application. During local development
 *     those may grant access to a real environment, which must never be reachable by accident.</li>
 *     <li>Read clients have multipart operation disabled, so downloads are ordinary single S3 GET
 *     requests. Write clients keep multipart operation enabled, so uploads over the part size
 *     transparently become multipart uploads with parallel part uploads. Both kinds share one caller
 *     supplied HTTP transport: a client built with {@link S3AsyncClientBuilder#httpClient} does not take
 *     ownership of that transport, the caller stays responsible for closing it.</li>
 * </ul>
 */
public final class S3ClientFactory {

    private S3ClientFactory() {
    }

    /**
     * Size of a part for multipart uploads: a trade-off between buffer memory usage, multipart overhead,
     * and the S3 limit of 10000 parts per upload. With 50 MiB parts, objects can grow to slightly over
     * 0.5 TB.
     */
    private static final long PART_SIZE = 50L * 1024 * 1024;

    /**
     * Creates the S3 client. Uploads over the part size transparently become multipart uploads, with
     * parallel part uploads.
     *
     * @param reuseConnections whether http connections may be re-used across requests. When {@code false},
     * every request asks the server to close the connection with a {@code Connection: close} header. The
     * client only keeps a connection when the response says it may, so this is what stops re-use.
     */
    public static S3AsyncClient createS3AsyncClient(String endpoint, String accessKey, String secretKey,
            String region, boolean pathStyleAccess, NettyNioAsyncHttpClient.Builder httpClientBuilder,
            boolean reuseConnections) {
        return baseBuilder(endpoint, accessKey, secretKey, region, pathStyleAccess, reuseConnections)
                .multipartEnabled(true)
                .multipartConfiguration(MultipartConfiguration.builder()
                        .minimumPartSizeInBytes(PART_SIZE)
                        .build())
                .httpClientBuilder(httpClientBuilder)
                .build();
    }

    /**
     * Creates the S3 client used for downloads. Multipart operation is disabled, so downloads are
     * ordinary single S3 GET requests and stream without whole-object buffering. Native SDK retries apply
     * before the response stream is handed over.
     *
     * @param httpClient shared HTTP transport used for requests; stays owned by the caller and is not
     * closed together with the returned client
     * @param reuseConnections whether http connections may be re-used across requests (see
     * {@link #createS3AsyncClient} for details)
     */
    public static S3AsyncClient createS3ReadAsyncClient(String endpoint, String accessKey, String secretKey,
            String region, boolean pathStyleAccess, SdkAsyncHttpClient httpClient, boolean reuseConnections) {
        return baseBuilder(endpoint, accessKey, secretKey, region, pathStyleAccess, reuseConnections)
                .multipartEnabled(false)
                .httpClient(httpClient)
                .build();
    }

    /**
     * Creates the S3 client used for uploads and deletes. Uploads over the part size transparently become
     * multipart uploads, with parallel part uploads.
     *
     * @param httpClient shared HTTP transport used for requests; stays owned by the caller and is not
     * closed together with the returned client
     * @param reuseConnections whether http connections may be re-used across requests (see
     * {@link #createS3AsyncClient} for details)
     */
    public static S3AsyncClient createS3WriteAsyncClient(String endpoint, String accessKey, String secretKey,
            String region, boolean pathStyleAccess, SdkAsyncHttpClient httpClient, boolean reuseConnections) {
        return baseBuilder(endpoint, accessKey, secretKey, region, pathStyleAccess, reuseConnections)
                .multipartEnabled(true)
                .multipartConfiguration(MultipartConfiguration.builder()
                        .minimumPartSizeInBytes(PART_SIZE)
                        .build())
                .httpClient(httpClient)
                .build();
    }

    /**
     * Creates the shared HTTP transport for the managed read and write S3 clients. A
     * {@code connectionPoolSize} of 0 (the default) means connections must not be re-used at all
     * (ACC-2696): no-reuse is then enforced with a {@code Connection: close} header on every request
     * instead of pool settings. With a pool, its size caps the number of concurrent connections, and idle
     * connections are kept around for the keep-alive period.
     */
    public static SdkAsyncHttpClient createSharedHttpClient(int connectionPoolSize,
            int connectionPoolKeepAliveSeconds) {
        var builder = NettyNioAsyncHttpClient.builder();
        if (connectionPoolSize > 0) {
            builder.maxConcurrency(connectionPoolSize)
                    .connectionMaxIdleTime(Duration.ofSeconds(connectionPoolKeepAliveSeconds));
        }
        return builder.build();
    }

    private static S3AsyncClientBuilder baseBuilder(String endpoint, String accessKey, String secretKey,
            String region, boolean pathStyleAccess, boolean reuseConnections) {
        return S3AsyncClient.builder()
                .endpointOverride(endpointUri(endpoint))
                .forcePathStyle(pathStyleAccess)
                .region(regionOrNone(region))
                .credentialsProvider(credentialsProvider(accessKey, secretKey))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(overrideConfiguration(reuseConnections));
    }

    private static URI endpointUri(String endpoint) {
        if (endpoint == null) {
            throw new IllegalArgumentException("endpoint is required");
        }
        var uri = URI.create(endpoint);
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "endpoint '%s' must include a scheme (http:// or https://)".formatted(endpoint));
        }
        return uri;
    }

    private static Region regionOrNone(String region) {
        return Region.of(region != null ? region : "none");
    }

    private static StaticCredentialsProvider credentialsProvider(String accessKey, String secretKey) {
        if (accessKey == null || secretKey == null) {
            throw new IllegalArgumentException("access-key and secret-key are required");
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private static Consumer<ClientOverrideConfiguration.Builder> overrideConfiguration(boolean reuseConnections) {
        return config -> {
            config.defaultProfileFile(ProfileFile.aggregator().build());
            if (!reuseConnections) {
                config.putHeader("Connection", "close");
            }
        };
    }
}
