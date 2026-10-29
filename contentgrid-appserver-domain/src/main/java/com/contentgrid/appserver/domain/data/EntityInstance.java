package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.version.Version;
import java.util.Collection;
import java.util.Optional;
import java.util.SequencedMap;

public interface EntityInstance {

    EntityIdentity getIdentity();

    SequencedMap<String, PlainDataEntry> getData();

    Collection<EntityLinkData> getLinks();

    Optional<Version> getContentVersion(ContentAttribute contentAttribute);

    default boolean hasContent(ContentAttribute contentAttribute) {
        // Content data is only mapped to a map when content is present, otherwise it is null
        return getData().get(contentAttribute.getName().getValue()) instanceof MapDataEntry;
    }
}
