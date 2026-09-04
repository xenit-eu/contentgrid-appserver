package com.contentgrid.appserver.integration.test.content;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.settings.ApplicationSettings;
import com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionSettings;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.contentstore.api.ContentAccessor;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import com.contentgrid.appserver.contentstore.impl.encryption.engine.DataEncryptionAlgorithm;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.KeyBytes;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.StoredDataEncryptionKey;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.TableStorageDataEncryptionKeyAccessor;
import com.contentgrid.appserver.contentstore.impl.encryption.keys.WrappingKeyId;
import com.contentgrid.appserver.query.engine.api.CreateEventConsumer;
import com.contentgrid.appserver.query.engine.api.DeleteEventConsumer;
import com.contentgrid.appserver.query.engine.api.LinkEventConsumer;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.UnlinkEventConsumer;
import com.contentgrid.appserver.query.engine.api.UpdateEventConsumer;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.ThunkExpression;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "contentgrid.security.unauthenticated.allow = true",
                "contentgrid.security.csrf.disabled = true",
                "contentgrid.appserver.content-store.type = ephemeral",
                "contentgrid.thunx.abac.source = none",
                "contentgrid.events.rabbitmq.enabled=false",
                "spring.datasource.url=jdbc:tc:postgresql:15:///",
                "contentgrid.appserver.content.encryption.engine.algorithms[0]=AES128_CTR",
                "contentgrid.appserver.content.encryption.engine.algorithms[1]=ALFRESCO",
        })
class EncryptedAlfCompatibilityTest {

    private static final ThunkExpression<Boolean> PERMIT_ALWAYS = Scalar.of(true);

    private static final NoneEvents NONE_EVENTS = new NoneEvents();

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("default"))
            .settings(ApplicationSettings.builder()
                    .contentEncryption(ContentEncryptionSettings.builder()
                            .enabled(true)
                            .build())
                    .build())
            .entity(Entity.builder()
                    .name(EntityName.of("employee"))
                    .table(TableName.of("employee"))
                    .pathSegment(PathSegmentName.of("employees"))
                    .linkName(LinkName.of("employee"))
                    .attribute(SimpleAttribute.builder()
                            .name(AttributeName.of("name"))
                            .column(ColumnName.of("name"))
                            .type(Type.TEXT)
                            .build())
                    .attribute(ContentAttribute.builder()
                            .name(AttributeName.of("file"))
                            .pathSegment(PathSegmentName.of("file"))
                            .linkName(LinkName.of("file"))
                            .idColumn(ColumnName.of("file__id"))
                            .filenameColumn(ColumnName.of("file__filename"))
                            .mimetypeColumn(ColumnName.of("file__mimetype"))
                            .lengthColumn(ColumnName.of("file__length"))
                            .build())
                    .build())
            .build();

    private static final String TEXT = "Hello world!";

    private static final byte[] CONTENT = TEXT.getBytes(StandardCharsets.UTF_8);

    private static final String FILENAME = "hello.txt";

    // the following static variables correlate to another and must have consistent order
    // KEYS = symmetric encryption keys generated in an alfresco-simple-content-stores ACS instance
    // RESOURCES = encrypted/decrypted resources (lorem ipsum-like)
    // ALGORITHMS = encryption algorithms used for resources
    // DECRYPTED_SIZES = unencrypted resource sizes
    // ENCRYPTED_SIZES = encrypted resource sizes
    private static final String[] KEYS = { "156c8bc259cd3e4ad1d9c38cf6361847", "566cf243d7ee9cf69df6ec004bf5f35c",
            "2405ae5a16b3909abb9ece58833c08bc", "72c51fcefd0a6da181dc4c98bbcc08e5", "89cf56ab800a394d95c10548065aae5d", "e39d23642ca81c68",
            "13320e7520e60e26133ef8372a0b7aad9e9e8a0bba5bf2ab" };

    private static final String[] RESOURCES = { "3d9ea5f1-807a-486a-810a-81c871adc52f.bin", "fe01c226-b391-4ec0-943f-ee290bb08e4e.bin",
            "32753a7a-ef36-4cb8-a4a5-c7f9f180f51e.bin", "bd78632e-80e2-42ed-8e05-e2f6b69a89b9.bin",
            "bc49e9ad-fcfc-403e-ba3e-2280d602a53e.bin", "ba50f10d-4df4-4ba0-9161-3038be5fab80.bin",
            "05efb43b-2808-4a1f-b25a-83f1c24f8bcf.bin" };

    private static final String[] ALGORITHMS = { "Alfresco-AES", "Alfresco-AES", "Alfresco-AES", "Alfresco-AES", "Alfresco-AES", "Alfresco-DES", "Alfresco-DESede" };

    private static final long[] DECRYPTED_SIZES = { 6094l, 4240l, 4117l, 4162l, 1001l, 2859l, 5853l };

    private static final long[] START_BYTES = { 128l, 3096l, 1234l, 3210l, 512l, 1536l, 4096l };

    private static final List<Arguments> simulateContentMigrationAndAccess;
    static {
        simulateContentMigrationAndAccess = new ArrayList<>();
        for (int i = 0; i < KEYS.length; i++)
        {
            simulateContentMigrationAndAccess.add(Arguments.of(ALGORITHMS[i], KEYS[i], RESOURCES[i], DECRYPTED_SIZES[i], START_BYTES[i]));
        }
    }

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @Autowired
    @Qualifier("ephemeralContentStoreResolver")
    private ContentStoreResolver contentStoreResolver;

    @Autowired
    private QueryEngine queryEngine;

    @Autowired
    private DSLContext dslContext;

    @BeforeEach
    void setup() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:%s".formatted(port))
                .responseTimeout(Duration.ofMinutes(10)) // For debugging
                .build();
    }

    @Test
    void createNewContentWhileInCompatibilityMode() {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part("file", new ByteArrayResource(CONTENT), MediaType.TEXT_PLAIN)
                .filename(FILENAME);
        multipartBodyBuilder.part("name", "test");

        // Upload content
        var response = client.post()
                .uri("/employees", port)
                .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
                .exchange()
                .expectStatus().isCreated()
                .returnResult(Void.class);

        var contentUrl = response.getResponseHeaders().getLocation() + "/file";

        // full content
        client.get().uri(contentUrl)
                .accept(MediaType.ALL)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(byte[].class).isEqualTo(CONTENT);

        var start = 5;
        var end = 9;

        var expected = Arrays.copyOfRange(CONTENT, start, end + 1);

        client.get().uri(contentUrl)
                .accept(MediaType.ALL)
                .header(HttpHeaders.RANGE, "bytes=%s-%s".formatted(start, end))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectBody(byte[].class).isEqualTo(expected);
    }

    @ParameterizedTest
    @FieldSource
    void simulateContentMigrationAndAccess(String alg, String keyHex, String resource, long decryptedSize,
            long startByte) throws Exception
    {
        // it is not possible to use ReST API to create migrated content
        // migration scripts (as far as I know) use direct DB load + store access

        var encryptedResource = "/alfresco/encrypted/" + resource;
        var decryptedResource = "/alfresco/decrypted/" + resource;
        var keyBytes = KeyBytes.adopt(HexFormat.of().parseHex(keyHex));

        // write file as stored in Alfresco
        ContentAccessor written;
        var contentStore = contentStoreResolver.resolve(APPLICATION);
        try (InputStream is = EncryptedAlfCompatibilityTest.class.getResourceAsStream(encryptedResource)) {
            written = contentStore.writeContent(is);
        }

        // record content-associated key 
        var dkeAccessor = new TableStorageDataEncryptionKeyAccessor(dslContext);
        dkeAccessor.addKey(written.getReference(),
                new StoredDataEncryptionKey(
                        DataEncryptionAlgorithm.of(alg),
                        WrappingKeyId.unwrapped(), keyBytes, new byte[0]));

        // create entity creation data (typically done in DatamodelApi)
        // need to save the encrypted size (actual length is technically unknown internally)
        String fileName = "test-" + UUID.randomUUID();
        var compositeContent = CompositeAttributeData.builder()
                .name(AttributeName.of("file"))
                .attribute(new SimpleAttributeData<>(AttributeName.of("id"), written.getReference().getValue()))
                .attribute(new SimpleAttributeData<>(AttributeName.of("filename"), fileName))
                .attribute(new SimpleAttributeData<>(AttributeName.of("mimetype"), "text/plain"))
                .attribute(new SimpleAttributeData<>(AttributeName.of("length"), decryptedSize))
                .build();
        var entity = EntityCreateData.builder()
                .entityName(EntityName.of("employee"))
                .attribute(new SimpleAttributeData<>(AttributeName.of("name"), fileName))
                .attribute(compositeContent)
                .build();

        // store entity
        EntityData entityData = queryEngine.create(APPLICATION, entity, PERMIT_ALWAYS, NONE_EVENTS);
        String id = entityData.getId().getValue().toString();

        // Read the full file using the REST API
        client.get()
                .uri("/employees/" + id + "/file", port)
                .accept(MediaType.ALL)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentLength(decryptedSize)
                .expectBody(byte[].class).isEqualTo(EncryptedAlfCompatibilityTest.class.getResourceAsStream(decryptedResource).readAllBytes());

        // Read a part of the file using the REST API
        var start = startByte;
        var end = startByte + (decryptedSize - startByte) / 2;

        var expected = new byte[(int)(end - start + 1)];
        try (InputStream is = EncryptedAlfCompatibilityTest.class.getResourceAsStream(decryptedResource)) {
            is.skipNBytes(start);
            is.readNBytes(expected, 0, expected.length);
        }

        client.get()
                .uri("/employees/" + id + "/file", port)
                .accept(MediaType.ALL)
                .header(HttpHeaders.RANGE, "bytes=%s-%s".formatted(start, end))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectBody(byte[].class).isEqualTo(expected);
    }

    @SpringBootApplication
    static class TestApp {

        @Bean
        ApplicationResolver applicationResolver() {
            return new SingleApplicationResolver(APPLICATION);
        }

    }

    private static class NoneEvents
            implements CreateEventConsumer, DeleteEventConsumer, LinkEventConsumer, UnlinkEventConsumer, UpdateEventConsumer {

        @Override
        public void onEntityCreate(Application application, EntityData data) {
            // NO-OP
        }

        @Override
        public void onEntityDelete(Application application, EntityData data) {
            // NO-OP
        }

        @Override
        public void onLink(Application application, EntityData oldData, EntityData newData) {
            // NO-OP
        }

        @Override
        public void onUnlink(Application application, EntityData oldData, EntityData newData) {
            // NO-OP
        }

        @Override
        public void onEntityUpdate(Application application, EntityData oldData, EntityData newData) {
            // NO-OP
        }
    }
}
