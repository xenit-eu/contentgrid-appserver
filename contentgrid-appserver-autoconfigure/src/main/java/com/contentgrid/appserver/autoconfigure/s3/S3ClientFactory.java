package com.contentgrid.appserver.autoconfigure.s3;

import java.net.URI;
import java.util.Locale;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Creates {@link S3Client} instances configured the way the previously used minio client was, so that the
 * existing configuration properties keep their exact semantics after the switch to the AWS SDK:
 * <ul>
 *     <li>An endpoint without scheme gets {@code https://} prepended, and a path in the endpoint is
 *     rejected.</li>
 *     <li>Path-style access is used unless the endpoint is an AWS endpoint (minio derived this the same way).
 *     The AWS SDK would otherwise default to virtual-host style, which most S3-compatible stores do not
 *     support.</li>
 *     <li>Without an access key + secret key pair, requests are sent unsigned (anonymous), like minio did.</li>
 *     <li>Without a region, {@code us-east-1} is used. minio would look up the bucket region with a
 *     GetBucketLocation call instead, but in ContentGrid deployments the region is always provided.</li>
 *     <li>Checksums are only calculated/validated where the S3 API requires them. The SDK defaults would send
 *     CRC checksums with aws-chunked encoding that S3-compatible stores (Dell ECS, older MinIO, ...)
 *     reject.</li>
 *     <li>The default profile file is replaced with an empty one, so the SDK never reads {@code ~/.aws/config}
 *     or {@code ~/.aws/credentials}. minio never read those files, and a broken file there (e.g. written by a
 *     newer aws CLI) would otherwise break client construction.</li>
 * </ul>
 * Internal to the appserver auto-configuration; not intended as public API.
 */
public final class S3ClientFactory {

    private S3ClientFactory() {
    }

    public static S3Client createS3Client(String endpoint, String accessKey, String secretKey, String region,
            Apache5HttpClient.Builder httpClientBuilder) {
        var endpointUri = normalizeEndpoint(endpoint);
        return S3Client.builder()
                .endpointOverride(endpointUri)
                .forcePathStyle(!isAwsEndpoint(endpointUri))
                .region(region != null ? Region.of(region) : Region.US_EAST_1)
                .credentialsProvider(credentialsProvider(accessKey, secretKey))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(httpClientBuilder)
                .overrideConfiguration(config -> config.defaultProfileFile(ProfileFile.aggregator().build()))
                .build();
    }

    static URI normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be empty");
        }
        var uri = URI.create(endpoint.contains("://") ? endpoint : "https://" + endpoint);
        if (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")) {
            throw new IllegalArgumentException("no path allowed in endpoint '%s'".formatted(endpoint));
        }
        return uri;
    }

    static boolean isAwsEndpoint(URI endpoint) {
        var host = endpoint.getHost();
        if (host == null) {
            return false;
        }
        var lowerCaseHost = host.toLowerCase(Locale.ROOT);
        return lowerCaseHost.endsWith(".amazonaws.com") || lowerCaseHost.endsWith(".amazonaws.com.cn");
    }

    private static AwsCredentialsProvider credentialsProvider(String accessKey, String secretKey) {
        if (accessKey != null && secretKey != null) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return AnonymousCredentialsProvider.create();
    }
}
