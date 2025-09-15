package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.InstantDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.values.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuditAttributeMapper implements AttributeMapper<Optional<DataEntry>, Optional<DataEntry>> {

    public enum Mode { CREATE, UPDATE }

    private final Mode mode;
    private final User user;
    private final Clock clock;

    @Override
    public Optional<DataEntry> mapAttribute(Attribute attribute, Optional<DataEntry> inputData)
            throws InvalidPropertyDataException {
        return mapAttribute(new SimpleAttributePath(attribute.getName()), attribute, inputData);
    }

    protected Optional<DataEntry> mapAttribute(AttributePath path, Attribute attribute, Optional<DataEntry> inputData)
            throws InvalidPropertyDataException {
        try {
            return switch (attribute) {
                case SimpleAttribute simpleAttribute -> mapSimpleAttribute(simpleAttribute, inputData);
                case CompositeAttribute compositeAttribute -> mapCompositeAttribute(path, compositeAttribute, inputData);
            };
        } catch (InvalidDataException e) {
            throw e.withinProperty(attribute.getName());
        }
    }

    protected Optional<DataEntry> mapSimpleAttribute(SimpleAttribute simpleAttribute, Optional<DataEntry> inputData) {
        if ((simpleAttribute.hasFlag(CreatedDateFlag.class) && mode == Mode.CREATE)
                || simpleAttribute.hasFlag(ModifiedDateFlag.class)) {
            return Optional.of(new InstantDataEntry(Instant.now(clock)));
        }
        return inputData;
    }

    protected Optional<DataEntry> mapCompositeAttribute(AttributePath path, CompositeAttribute compositeAttribute,
            Optional<DataEntry> inputData) throws InvalidDataException {
        if (compositeAttribute instanceof UserAttribute userAttr && (
                (userAttr.hasFlag(CreatorFlag.class) && mode == Mode.CREATE) || userAttr.hasFlag(ModifierFlag.class))) {
            return Optional.of(MapDataEntry.builder()
                    .item(userAttr.getId().getName().getValue(), new StringDataEntry(user != null ? user.getId() : "<none>"))
                    .item(userAttr.getNamespace().getName().getValue(), new StringDataEntry(user != null ? user.getNamespace() != null ? user.getNamespace() : "<none>" : "<none>"))
                    .item(userAttr.getUsername().getName().getValue(), new StringDataEntry(user != null ? user.getName() : "<none>"))
                    .build()
            );
        } else if (inputData.isPresent() && inputData.get() instanceof MapDataEntry mapDataEntry) {
            var builder = MapDataEntry.builder();
            for (var attribute : compositeAttribute.getAttributes()) {
                Optional<DataEntry> entry = Optional.of(mapDataEntry.get(attribute.getName().getValue()));
                mapAttribute(path.withSuffix(attribute.getName()), attribute, entry)
                        .ifPresent(dataEntry -> builder.item(attribute.getName().getValue(), (PlainDataEntry) dataEntry));
            }
            return Optional.of(builder.build());
        } else if (inputData.isPresent() && inputData.get() instanceof MissingDataEntry || inputData.isEmpty()) {
            var builder = MapDataEntry.builder();
            for (var attribute : compositeAttribute.getAttributes()) {
                mapAttribute(path.withSuffix(attribute.getName()), attribute, Optional.empty())
                        .ifPresent(dataEntry -> builder.item(attribute.getName().getValue(), (PlainDataEntry) dataEntry));
            }
            var res = builder.build();
            if (res.size() > 0) {
                return Optional.of(builder.build());
            }
            return inputData;
        }
        return inputData;
    }

}
