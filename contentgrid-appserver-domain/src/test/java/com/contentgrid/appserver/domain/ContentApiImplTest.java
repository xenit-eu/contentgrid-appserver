package com.contentgrid.appserver.domain;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.APPLICATION;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PRODUCT;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PRODUCT_PICTURE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.exception.UnsatisfiedVersionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentApiImplTest {

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private DatamodelApi datamodelApi;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private ContentStore contentStore;

    private ContentApi contentApi;

    private static final byte[] CONTENT_DATA = {1, 2, 3, 4};

    private static final Version ENTITY_VERSION = Version.exactly("this-version");

    @BeforeEach
    void setup() {
        contentApi = new ContentApiImpl(datamodelApi, contentStore);
    }

    @Test
    void findContentPresent() throws UnreadableContentException {
        var entityId = EntityId.of(UUID.randomUUID());
        Mockito.when(datamodelApi.findById(APPLICATION, EntityRequest.forEntity(PRODUCT.getName(), entityId)))
                .thenReturn(Optional.of(EntityData.builder()
                        .name(PRODUCT.getName())
                        .id(entityId)
                        .attribute(CompositeAttributeData.builder()
                                .name(PRODUCT_PICTURE.getName())
                                .attribute(new SimpleAttributeData<>(PRODUCT_PICTURE.getId().getName(), "content.bin"))
                                .attribute(new SimpleAttributeData<>(PRODUCT_PICTURE.getMimetype().getName(),
                                        "image/jpeg"))
                                .attribute(new SimpleAttributeData<>(PRODUCT_PICTURE.getLength().getName(), 123L))
                                .attribute(new SimpleAttributeData<>(PRODUCT_PICTURE.getFilename().getName(),
                                        "IMG_123.jpg"))
                                .build()
                        )
                        .build()));

        var maybeContent = contentApi.find(APPLICATION, PRODUCT.getName(), entityId, PRODUCT_PICTURE.getName());

        assertThat(maybeContent).hasValueSatisfying(content -> {
            assertThat(content.getLength()).isEqualTo(123);
            assertThat(content.getMimeType()).isEqualTo("image/jpeg");
            assertThat(content.getFilename()).isEqualTo("IMG_123.jpg");
        });

        // Fetching content object actually doesn't contact the store yet
        Mockito.verifyNoInteractions(contentStore);

        var contentReaderMock = Mockito.mock(ContentReader.class, Answers.RETURNS_SMART_NULLS);
        Mockito.when(contentStore.getReader(Mockito.eq(ContentReference.of("content.bin")), Mockito.any()))
                .thenReturn(contentReaderMock);
        Mockito.when(contentReaderMock.getContentInputStream()).thenReturn(new ByteArrayInputStream(CONTENT_DATA));

        assertThat(maybeContent).hasValueSatisfying(content -> {
            try {
                assertThat(content.getInputStream()).hasBinaryContent(CONTENT_DATA);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        Mockito.verify(contentStore).getReader(ContentReference.of("content.bin"), ResolvedContentRange.fullRange(123));
    }

    @Test
    void findContentAbsent() {
        var entityId = EntityId.of(UUID.randomUUID());
        Mockito.when(datamodelApi.findById(APPLICATION, EntityRequest.forEntity(PRODUCT.getName(), entityId)))
                .thenReturn(Optional.of(EntityData.builder()
                        .name(PRODUCT.getName())
                        .id(entityId)
                        .attribute(CompositeAttributeData.builder()
                                .name(PRODUCT_PICTURE.getName())
                                .attribute(new SimpleAttributeData<>(PRODUCT_PICTURE.getId().getName(), null))
                                .attribute(new SimpleAttributeData<>(PRODUCT_PICTURE.getMimetype().getName(), null))
                                .attribute(new SimpleAttributeData<>(PRODUCT_PICTURE.getLength().getName(), null))
                                .attribute(new SimpleAttributeData<>(PRODUCT_PICTURE.getFilename().getName(), null))
                                .build()
                        )
                        .build()));

        assertThat(contentApi.find(APPLICATION, PRODUCT.getName(), entityId, PRODUCT_PICTURE.getName())).isEmpty();

    }

    private CompositeAttributeData createContentData(String contentId) {
        var hasContent = contentId != null;
        return CompositeAttributeData.builder()
                .name(PRODUCT_PICTURE.getName())
                .attribute(new SimpleAttributeData<>(PRODUCT_PICTURE.getId().getName(), contentId))
                .attribute(new SimpleAttributeData<>(PRODUCT_PICTURE.getLength().getName(), hasContent?140:null))
                .attribute(new SimpleAttributeData<>(PRODUCT_PICTURE.getMimetype().getName(), hasContent?"text/plain":null))
                .attribute(new SimpleAttributeData<>(PRODUCT_PICTURE.getFilename().getName(), hasContent?"readme.txt":null))
                .build();
    }

    @SneakyThrows
    private void setupEntity(EntityId entityId, boolean hasContent) {
        Mockito.when(datamodelApi.findById(APPLICATION, EntityRequest.forEntity(PRODUCT.getName(), entityId)))
                .thenReturn(Optional.of(new EntityData(
                        EntityIdentity.forEntity(PRODUCT.getName(), entityId)
                                .withVersion(ENTITY_VERSION),
                        List.of(
                                createContentData(hasContent?"content-id":null)
                        )
                )));

    }

    public static Stream<Arguments> succeedingConstraints() {
        return Stream.of(
                Arguments.of(VersionConstraint.ANY, true),
                Arguments.of(VersionConstraint.ANY, false),
                Arguments.of(Version.unspecified(), true),
                // This is an implementation detail: version hash is calculated from content id + mimetype
                Arguments.of(Version.exactly(ContentApiImpl.hash("content-id", "text/plain")), true),
                Arguments.of(Version.nonExisting(), false)
        );
    }

    public static Stream<Arguments> failingConstraints() {
        return Stream.of(
                Arguments.of(Version.nonExisting(), true),
                Arguments.of(Version.unspecified(), false),
                Arguments.of(Version.exactly("not-this-content-id"), true),
                Arguments.of(Version.exactly("not-this-content-id"), false)
        );
    }

    @ParameterizedTest(name = "constraint={0} hasContent={1}")
    @MethodSource("succeedingConstraints")
    void update_success(VersionConstraint constraint, boolean hasContent) throws InvalidPropertyDataException {
        var entityId = EntityId.of(UUID.randomUUID());

        setupEntity(entityId, hasContent);

        Mockito.when(datamodelApi.updatePartial(Mockito.eq(APPLICATION), Mockito.<EntityData>any(), Mockito.any()))
                .thenAnswer(args -> {
                    var originalData = args.getArgument(1, EntityData.class);
                    return new EntityData(
                            originalData.getIdentity()
                                    .withVersion(Version.exactly("new-version")),
                            List.of(
                                    createContentData("new-content-id")
                            )
                    );
                });

        var file = new FileDataEntry("my-file.jpg", "image/jpeg", InputStream::nullInputStream);
        contentApi.update(APPLICATION, PRODUCT.getName(), entityId, PRODUCT_PICTURE.getName(), constraint, file);

        Mockito.verify(datamodelApi).updatePartial(
                Mockito.eq(APPLICATION),
                Mockito.<EntityData>assertArg(entityData -> {
                    assertThat(entityData.getIdentity().getEntityId()).isEqualTo(entityId);
                    assertThat(entityData.getIdentity().getVersion()).isEqualTo(ENTITY_VERSION);
                }),
                Mockito.assertArg(inputData -> {
                    assertThat(inputData.get("picture", FileDataEntry.class)).isEqualTo(file);
                }));

        Mockito.verifyNoMoreInteractions(datamodelApi, contentStore);
    }

    @ParameterizedTest(name = "constraint={0} hasContent={1}")
    @MethodSource("failingConstraints")
    void update_fails(VersionConstraint constraint, boolean hasContent) {
        var entityId = EntityId.of(UUID.randomUUID());

        setupEntity(entityId, hasContent);

        var file = new FileDataEntry("my-file.jpg", "image/jpeg", InputStream::nullInputStream);
        assertThatThrownBy(() -> {
            contentApi.update(APPLICATION, PRODUCT.getName(), entityId, PRODUCT_PICTURE.getName(), constraint, file);
        }).isInstanceOf(UnsatisfiedVersionException.class);

        Mockito.verifyNoMoreInteractions(datamodelApi, contentStore);
    }

    @ParameterizedTest(name = "constraint={0} hasContent={1}")
    @MethodSource("succeedingConstraints")
    void delete_success(VersionConstraint versionConstraint, boolean hasContent) throws InvalidPropertyDataException {
        var entityId = EntityId.of(UUID.randomUUID());

        setupEntity(entityId, hasContent);
        Mockito.when(datamodelApi.updatePartial(Mockito.eq(APPLICATION), Mockito.<EntityData>any(), Mockito.any()))
                .thenAnswer(args -> {
                    var originalData = args.getArgument(1, EntityData.class);
                    return new EntityData(
                            originalData.getIdentity()
                                    .withVersion(Version.exactly("new-version")),
                            List.of(
                                    createContentData(null)
                            )
                    );
                });

        contentApi.delete(APPLICATION, PRODUCT.getName(), entityId, PRODUCT_PICTURE.getName(), versionConstraint);

        Mockito.verify(datamodelApi).updatePartial(
                Mockito.eq(APPLICATION),
                Mockito.<EntityData>assertArg(entityData -> {
                    assertThat(entityData.getIdentity().getEntityId()).isEqualTo(entityId);
                    assertThat(entityData.getIdentity().getVersion()).isEqualTo(ENTITY_VERSION);
                }),
                Mockito.assertArg(inputData -> {
                    assertThat(inputData.get("picture", FileDataEntry.class)).isEqualTo(NullDataEntry.INSTANCE);
                }));

        Mockito.verifyNoMoreInteractions(datamodelApi, contentStore);
    }

    @ParameterizedTest(name = "constraint={0} hasContent={1}")
    @MethodSource("failingConstraints")
    void delete_fails(VersionConstraint versionConstraint, boolean hasContent) {
        var entityId = EntityId.of(UUID.randomUUID());

        setupEntity(entityId, hasContent);

        assertThatThrownBy(() -> {
            contentApi.delete(APPLICATION, PRODUCT.getName(), entityId, PRODUCT_PICTURE.getName(), versionConstraint);
        }).isInstanceOf(UnsatisfiedVersionException.class);

        Mockito.verifyNoMoreInteractions(datamodelApi, contentStore);
    }
}
