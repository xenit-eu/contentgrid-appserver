package com.contentgrid.appserver.domain.data;

import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PRODUCT;
import static com.contentgrid.appserver.application.model.fixtures.ModelTestFixtures.PRODUCT_PICTURE;
import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.version.Version;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityInstanceTest {

    @Test
    void hasContent_contentPresent() {
        var instance = createInstance(MapDataEntry.builder()
                .item(PRODUCT_PICTURE.getMimetype().getName().getValue(), new StringDataEntry("text/plain"))
                .build());

        assertThat(instance.hasContent(PRODUCT_PICTURE)).isTrue();
    }

    @Test
    void hasContent_contentNull() {
        var instance = createInstance(NullDataEntry.INSTANCE);

        assertThat(instance.hasContent(PRODUCT_PICTURE)).isFalse();
    }

    @Test
    void hasContent_contentAttributeAbsent() {
        var instance = createInstance(null);

        assertThat(instance.hasContent(PRODUCT_PICTURE)).isFalse();
    }

    private static EntityInstance createInstance(PlainDataEntry contentData) {
        var data = new LinkedHashMap<String, PlainDataEntry>();
        if (contentData != null) {
            data.put(PRODUCT_PICTURE.getName().getValue(), contentData);
        }
        return new TestEntityInstance(
                EntityIdentity.forEntity(PRODUCT.getName(), EntityId.of(UUID.randomUUID())),
                data
        );
    }

    record TestEntityInstance(EntityIdentity identity, SequencedMap<String, PlainDataEntry> data)
            implements EntityInstance {

        @Override
        public EntityIdentity getIdentity() {
            return identity;
        }

        @Override
        public SequencedMap<String, PlainDataEntry> getData() {
            return data;
        }

        @Override
        public Collection<EntityLinkData> getLinks() {
            return List.of();
        }

        @Override
        public Optional<Version> getContentVersion(ContentAttribute contentAttribute) {
            return Optional.empty();
        }
    }
}
