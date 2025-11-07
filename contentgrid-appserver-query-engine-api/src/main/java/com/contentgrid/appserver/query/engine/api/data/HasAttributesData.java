package com.contentgrid.appserver.query.engine.api.data;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.CompositeAttributePath;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

public interface HasAttributesData {

    List<AttributeData> getAttributes();

    Optional<AttributeData> getAttributeByName(AttributeName name);

    default Optional<SimpleAttributeData<?>> getNestedAttributeByPath(@NonNull AttributePath path) {
        var maybeAttributeData = getAttributeByName(path.getFirst());
        return switch (path) {
            case SimpleAttributePath simpleAttributePath -> maybeAttributeData.flatMap(attr -> {
                if (attr instanceof SimpleAttributeData<?> simpleAttributeData) {
                    return Optional.of(simpleAttributeData);
                }
                return Optional.empty();
            });
            case CompositeAttributePath compositeAttributePath -> maybeAttributeData.flatMap(attr -> {
                if(attr instanceof HasAttributesData hasAttributesData) {
                    return hasAttributesData.getNestedAttributeByPath(path.getRest());
                }
                return Optional.empty();
            });
        };
    }

}
