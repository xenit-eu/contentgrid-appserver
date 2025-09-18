package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidDataFormatException;
import com.contentgrid.appserver.domain.data.type.DataType;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

@RequiredArgsConstructor
public class ContentUploadAttributeMapper extends AbstractDescendingAttributeMapper {

    private final ContentStore contentStore;

    @Override
    protected Optional<DataEntry> mapSimpleAttribute(AttributePath path, SimpleAttribute simpleAttribute, DataEntry inputData) {
        return Optional.of(inputData);
    }

    @Override
    protected Optional<DataEntry> mapCompositeAttribute(AttributePath path, CompositeAttribute compositeAttribute, DataEntry inputData) throws InvalidDataException {
        var result = super.mapCompositeAttribute(path, compositeAttribute, inputData);
        if(compositeAttribute instanceof ContentAttribute contentAttribute && inputData instanceof MapDataEntry mapDataEntry) {
            // Remove file id and size from attributes that can be set
            var blockedAttributes = Set.of(
                    contentAttribute.getId().getName().getValue(),
                    contentAttribute.getLength().getName().getValue()
            );
            var newMapBuilder = MapDataEntry.builder();
            mapDataEntry.getItems()
                    .entrySet()
                    .stream()
                    .filter(item -> !blockedAttributes.contains(item.getKey()))
                    .forEach(entry -> newMapBuilder.item(entry.getKey(), entry.getValue()));
            return Optional.of(newMapBuilder.build());
        }
        return result;
    }

    @Override
    protected Optional<DataEntry> mapCompositeAttributeUnsupportedDatatype(AttributePath path, CompositeAttribute attribute, DataEntry inputData) throws InvalidDataException {
        if(attribute instanceof ContentAttribute contentAttribute && inputData instanceof FileDataEntry fileDataEntry) {
            MimeType mimeType;
            try {
                mimeType = MimeTypeUtils.parseMimeType(fileDataEntry.getContentType());
                if(!mimeType.isConcrete()) {
                    throw new InvalidMimeTypeException(fileDataEntry.getContentType(), "Must be concrete");
                }
            } catch (InvalidMimeTypeException invalidMimeTypeException) {
                throw new InvalidDataFormatException(DataType.of(FileDataEntry.class), invalidMimeTypeException);
            }
            try {
                var contentAccessor = contentStore.writeContent(fileDataEntry.getInputStream());

                var builder = MapDataEntry.builder();
                builder.item(contentAttribute.getId().getName().getValue(), new StringDataEntry(contentAccessor.getReference().getValue()))
                        .item(contentAttribute.getLength().getName().getValue(), new LongDataEntry(contentAccessor.getContentSize()))
                        .item(contentAttribute.getMimetype().getName().getValue(), new StringDataEntry(mimeType.toString()));

                if(fileDataEntry.getFilename() != null) {
                    builder.item(contentAttribute.getFilename().getName().getValue(), new StringDataEntry(fileDataEntry.getFilename()));
                }

                return Optional.of(builder.build());
            } catch (UnwritableContentException|IOException e) {
                throw new RuntimeException(e);
            }

        }
        return Optional.of(inputData);
    }
}
