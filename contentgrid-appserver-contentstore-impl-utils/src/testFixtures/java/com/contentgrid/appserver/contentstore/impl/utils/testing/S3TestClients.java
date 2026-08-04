package com.contentgrid.appserver.contentstore.impl.utils.testing;

import java.net.URI;
import lombok.experimental.UtilityClass;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Creates S3 clients for tests, with every setting pinned explicitly so the SDK never reads
 * {@code ~/.aws} or other environment configuration of the machine running the tests.
 */
@UtilityClass
public class S3TestClients {

    private static final long PART_SIZE = 50L * 1024 * 1024; // must match the production part size

    public static S3Client s3Client(String endpoint) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .forcePathStyle(true)
                .region(Region.of("none"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(Apache5HttpClient.builder())
                .overrideConfiguration(config -> config.defaultProfileFile(ProfileFile.aggregator().build()))
                .build();
    }

    public static S3AsyncClient s3AsyncClient(String endpoint) {
        return S3AsyncClient.builder()
                .endpointOverride(URI.create(endpoint))
                .forcePathStyle(true)
                .region(Region.of("none"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .multipartEnabled(true)
                .multipartConfiguration(config -> config
                        .thresholdInBytes(PART_SIZE)
                        .minimumPartSizeInBytes(PART_SIZE))
                .overrideConfiguration(config -> config.defaultProfileFile(ProfileFile.aggregator().build()))
                .build();
    }
}
