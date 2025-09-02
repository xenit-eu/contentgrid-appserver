package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AuditHelper2 {
    private static final Set<AttributeFlag> auditFlags = Set.of(
            CreatorFlag.INSTANCE,
            CreatedDateFlag.INSTANCE,
            ModifierFlag.INSTANCE,
            ModifiedDateFlag.INSTANCE
    );

    public static List<AttributeData> contributeAuditMetadata(Entity entity) {
        List<AttributeData> results = new ArrayList<>();

        for (Attribute attr : entity.getAttributes()) {
            Optional<AttributeData> data = switch(attr) {
                case SimpleAttribute simple -> addSimpleAuditMetadata(simple);
                case CompositeAttribute composite -> addCompositeAuditMetadata(composite);
            };
            data.ifPresent(results::add);
        }

        return results;
    }

    private static Optional<AttributeData> addCompositeAuditMetadata(CompositeAttribute composite) {
        // If the composite itself has a flag, i.e. user attributes
        for (var flag : auditFlags) {
            if (composite.hasFlag(flag.getClass())) {
                return Optional.of(createAuditData(composite, flag));
            }
        }
        // Otherwise recurse
        var datas = new ArrayList<AttributeData>();
        for (var attr : composite.getAttributes()) {
            Optional<AttributeData> data = switch(attr) {
                case SimpleAttribute simp -> addSimpleAuditMetadata(simp);
                case CompositeAttribute comp -> addCompositeAuditMetadata(comp);
            };
            data.ifPresent(datas::add);
        }
        if (datas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(CompositeAttributeData.builder().name(composite.getName()).attributes(datas).build());
    }

    private static Optional<AttributeData> addSimpleAuditMetadata(SimpleAttribute simple) {
        for (var flag : auditFlags) {
            if (simple.hasFlag(flag.getClass())) {
                return Optional.of(createAuditData(simple, flag));
            }
        }
        return Optional.empty();
    }

    private static AttributeData createAuditData(Attribute attribute, AttributeFlag flag) {
        var flagType = flag.getClass();
        var attributeName = attribute.getName();
        if (CreatorFlag.class.equals(flagType) || ModifierFlag.class.equals(flagType)) {
            // TODO
            var userAttr = (UserAttribute) attribute;
            return CompositeAttributeData.builder().name(attributeName)
                    .attribute(new SimpleAttributeData<>(userAttr.getId().getName(), UUID.fromString("00000000-0000-0000-0000-000000000000")))
                    .attribute(new SimpleAttributeData<>(userAttr.getNamespace().getName(), "keycloak"))
                    .attribute(new SimpleAttributeData<>(userAttr.getUsername().getName(), "alice@example.com"))
                    .build();
        } else if (CreatedDateFlag.class.equals(flagType) || ModifiedDateFlag.class.equals(flagType)) {
            return new SimpleAttributeData<>(attributeName, Instant.now());
        }
        throw new IllegalArgumentException("Unknown audit metadata flag: " + flagType);
    }

}
