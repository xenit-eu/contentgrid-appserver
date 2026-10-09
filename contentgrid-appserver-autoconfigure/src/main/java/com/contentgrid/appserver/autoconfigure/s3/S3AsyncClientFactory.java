package com.contentgrid.appserver.autoconfigure.s3;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.PropertyMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.DelegatingS3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.utils.SdkAutoCloseable;

@RequiredArgsConstructor
public class S3AsyncClientFactory {
    @NonNull
    private final S3ConfigurationProperties configurationProperties;

    @NonNull
    private Consumer<NettyNioAsyncHttpClient.Builder> nettyBuilderCustomizer = b -> {};

    @NonNull
    private Consumer<S3AsyncClientBuilder> clientBuilderCustomizer = b -> {};

    /**
     * Size of a part for multipart uploads: a trade-off between buffer memory usage, multipart overhead,
     * and the S3 limit of 10000 parts per upload. With 50 MiB parts, objects can grow to slightly over
     * 0.5 TB.
     */
    private static final long PART_SIZE = 50L * 1024 * 1024;

    private SdkAsyncHttpClient createHttpClient() {
        var builder = NettyNioAsyncHttpClient.builder();

        var mapper = PropertyMapper.get();

        mapper.from(configurationProperties::connectionPoolSize)
                .when(size -> size > 0)
                .to(builder::maxConcurrency);
        // connection-pool-size 0 means connections must not be re-used at all (ACC-2696):
        // connections are expired as soon as possible, so they never return back into a pool
        mapper.from(configurationProperties::connectionPoolSize)
                .when(size -> size <= 0)
                .toCall(() -> builder.connectionTimeToLive(Duration.ofMillis(1)));


        mapper.from(configurationProperties::connectionPoolIdleTimeout).to(builder::connectionMaxIdleTime);

        mapper.from(configurationProperties::connectionTimeout).to(builder::connectionTimeout);
        mapper.from(configurationProperties::readTimeout).to(builder::readTimeout);
        mapper.from(configurationProperties::writeTimeout).to(builder::writeTimeout);


        nettyBuilderCustomizer.accept(builder);

        return builder.build();
    }

    public S3AsyncClientFactory customizeNetty(@NonNull Consumer<NettyNioAsyncHttpClient.Builder> customizer) {
        nettyBuilderCustomizer = nettyBuilderCustomizer.andThen(customizer);
        return this;
    }

    public S3AsyncClientFactory customizeClientBuilder(@NonNull Consumer<S3AsyncClientBuilder> customizer) {
        clientBuilderCustomizer = clientBuilderCustomizer.andThen(customizer);
        return this;
    }

    private S3AsyncClientBuilder createClientBuilder() {
        var builder = S3AsyncClient.builder()
                .endpointOverride(configurationProperties.endpoint())
                .forcePathStyle(configurationProperties.pathStyleAccess())
                .region(Region.of(Objects.requireNonNullElse(configurationProperties.region(), "none")))
                .credentialsProvider(createCredentialsProvider())
                // Checksums are only calculated/validated where the S3 API requires them, because S3-compatible
                // stores often reject the CRC trailers with aws-chunked encoding that the SDK sends by default.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(config -> {
                    // Prevent any AWS profile file to be read by default
                    // We don't want the SDK to pick up AWS configuration from the user account running the application,
                    // especially during development, as those may grant access to a real environment.
                    config.defaultProfileFile(ProfileFile.aggregator().build());
                });
        clientBuilderCustomizer.accept(builder);
        return builder;
    }

    private AwsCredentialsProvider createCredentialsProvider() {
        // If no access key is set, fall back to the default resolution of the AWS SDK
        if (configurationProperties.accessKey() == null) {
            return null;
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(configurationProperties.accessKey(), configurationProperties.secretKey()));
    }

    public S3AsyncClient createClient() {
        var httpClient = createHttpClient();
        var multipartClient = createClientBuilder()
                .multipartEnabled(true)
                .multipartConfiguration(multipart -> multipart.minimumPartSizeInBytes(PART_SIZE))
                .httpClient(httpClient)
                .build();

        var standardClient = createClientBuilder()
                .httpClient(httpClient)
                .build();

        return new MultipartUploadS3AsyncClient(standardClient, multipartClient)
                .alsoClose(httpClient);
    }

    /**
     * Custom S3 client that only uses multipart for uploading objects, not for downloading them
     */
    private static class MultipartUploadS3AsyncClient extends DelegatingS3AsyncClient {
        @NonNull
        private final S3AsyncClient multipartClient;

        private final Set<SdkAutoCloseable> alsoClose = HashSet.newHashSet(3);

        public MultipartUploadS3AsyncClient(
                @NonNull S3AsyncClient normalClient,
                @NonNull S3AsyncClient multipartClient
        ) {
            super(normalClient);
            this.multipartClient = multipartClient;
            alsoClose.add(multipartClient);
        }

        @Override
        public CompletableFuture<PutObjectResponse> putObject(PutObjectRequest putObjectRequest, AsyncRequestBody requestBody) {
            return multipartClient.putObject(putObjectRequest, requestBody);
        }

        private MultipartUploadS3AsyncClient alsoClose(@NonNull SdkAutoCloseable closeable) {
            alsoClose.add(closeable);
            return this;
        }

        @Override
        public void close() {
            super.close();
            alsoClose.forEach(SdkAutoCloseable::close);
        }
    }

}
