package com.contentgrid.appserver.domain;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.APPLICATION;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PRODUCT;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PRODUCT_PICTURE;
import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @BeforeEach
    void setup() {
        contentApi = new ContentApiImpl(datamodelApi, contentStore);
    }

    @Test
    void findContentPresent() throws UnreadableContentException {
        var entityId = EntityId.of(UUID.randomUUID());
        Mockito.when(datamodelApi.findById(APPLICATION, PRODUCT, entityId))
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
        Mockito.when(datamodelApi.findById(APPLICATION, PRODUCT, entityId))
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

    @Test
    void update() throws InvalidPropertyDataException {
        var entityId = EntityId.of(UUID.randomUUID());

        var file = new FileDataEntry("my-file.jpg", "image/jpeg", InputStream::nullInputStream);
        contentApi.update(APPLICATION, PRODUCT.getName(), entityId, PRODUCT_PICTURE.getName(), file);

        Mockito.verify(datamodelApi).updatePartial(
                Mockito.eq(APPLICATION),
                Mockito.eq(PRODUCT.getName()),
                Mockito.eq(entityId),
                Mockito.assertArg(inputData -> {
                    assertThat(inputData.get("picture", FileDataEntry.class)).isEqualTo(file);
                }));

        Mockito.verifyNoMoreInteractions(datamodelApi, contentStore);
    }

    @Test
    void delete() throws InvalidPropertyDataException {
        var entityId = EntityId.of(UUID.randomUUID());

        contentApi.delete(APPLICATION, PRODUCT.getName(), entityId, PRODUCT_PICTURE.getName());

        Mockito.verify(datamodelApi).updatePartial(
                Mockito.eq(APPLICATION),
                Mockito.eq(PRODUCT.getName()),
                Mockito.eq(entityId),
                Mockito.assertArg(inputData -> {
                    assertThat(inputData.get("picture", FileDataEntry.class)).isEqualTo(NullDataEntry.INSTANCE);
                }));

        Mockito.verifyNoMoreInteractions(datamodelApi, contentStore);
    }

}