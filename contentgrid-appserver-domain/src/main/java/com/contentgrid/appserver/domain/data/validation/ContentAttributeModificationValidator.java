package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.validation.AttributeValidationDataMapper.Validator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ContentAttributeModificationValidator implements Validator {
    private final EntityInstance entityData;

    @Override
    public void validate(AttributePath attributePath, Attribute attribute, DataEntry dataEntry)
            throws InvalidDataException {
        if (attribute instanceof ContentAttribute contentAttribute) {
            var hasContent = resolveData(attributePath)
                    .map(MapDataEntry.class::isInstance)
                    .orElse(false);
            if (dataEntry instanceof MapDataEntry mapDataEntry) {
                if (!hasContent) {
                    for (var contentSubAttribute : contentAttribute.getAttributes()) {
                        if (!isEmpty(mapDataEntry.get(contentSubAttribute.getName().getValue()))) {
                            throw new ContentMissingInvalidDataException(contentSubAttribute.getName());
                        }
                    }
                } else {
                    // When a content attribute is present, mimetype is required to be filled in
                    var mimeType = contentAttribute.getMimetype().getName();
                    // At the point that this validator runs, MissingDataEntry is already converted to null for create/update
                    // And has been set to missing only for partialUpdate. We don't have to require a mimetype input for partial update,
                    // as the mimetype will just not be set at all
                    if ((mapDataEntry.get(mimeType.getValue()) instanceof NullDataEntry)) {
                        throw new RequiredConstraintViolationInvalidDataException().withinProperty(mimeType);
                    }
                }
            } else if (hasContent && dataEntry instanceof NullDataEntry) {
                // TODO: mark content for deletion, it can only be deleted in ContentStore
                //  after database transaction has completed
            }
        }

    }

    private boolean isEmpty(DataEntry dataEntry) {
        return switch (dataEntry) {
            case NullDataEntry nullDataEntry-> true;
            case MissingDataEntry missingDataEntry -> true;
            default -> false;
        };
    }

    private Optional<PlainDataEntry> resolveData(AttributePath path) {
        if(entityData == null) {
            return Optional.empty();
        }
        var attributeData = entityData.getData().get(path.getFirst().getValue());
        var rest = path.getRest();
        while(rest != null) {
            if(attributeData instanceof MapDataEntry mapDataEntry) {
                attributeData = mapDataEntry.get(rest.getFirst().getValue());
            } else {
                log.warn("Data is not composite along path {}", path);
                return Optional.empty();
            }
            rest = rest.getRest();
        }

        return Optional.ofNullable(attributeData);
    }
}
