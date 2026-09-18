package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.application.model.propertypath.CompositeAttributePath;
import com.contentgrid.appserver.application.model.propertypath.SimpleAttributePath;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

public interface HasAttributesData {

    List<AttributeData> getAttributes();

    Optional<AttributeData> getAttributeByName(AttributeName name);

    default Optional<AttributeData> getNestedAttributeByPath(@NonNull AttributePath path) {
        return getNestedAttributeByPath(path, AttributeData.class);
    }

    default <T extends AttributeData> Optional<T> getNestedAttributeByPath(@NonNull AttributePath path,
            @NonNull Class<T> attributeDataClass) {
        var maybeAttributeData = getAttributeByName(path.getFirst());
        if (path.getRest() == null) {
            return maybeAttributeData
                    .filter(attributeDataClass::isInstance)
                    .map(attributeDataClass::cast);
        }
        return maybeAttributeData
                .filter(HasAttributesData.class::isInstance)
                .map(HasAttributesData.class::cast)
                .flatMap(hasAttributesData -> hasAttributesData.getNestedAttributeByPath(path.getRest(),
                        attributeDataClass));
    }
}
