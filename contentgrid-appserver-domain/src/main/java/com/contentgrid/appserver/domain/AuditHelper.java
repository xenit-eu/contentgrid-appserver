package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.CompositeAttributePath;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AuditHelper {
    public static Optional<AttributePath> findAttributeWithFlag(List<Attribute> attributes, AttributeFlag flag) {
        // depth first, because audit fields are usually nested
        for (Attribute attr : attributes) {
            if (attr.hasFlag(flag.getClass())) {
                return Optional.of(new SimpleAttributePath(attr.getName()));
            } else if (attr instanceof CompositeAttribute comp) {
                var result = findAttributeWithFlag(comp.getAttributes(), flag);
                if (result.isPresent()) {
                    return result.map(p -> p.withPrefix(comp.getName()));
                }
            }
        }
        return Optional.empty();
    }

    @SafeVarargs
    public static List<AttributeData> findAuditFieldsForFlags(List<Attribute> attributes, Class<? extends AttributeFlag>... flagTypes) {
        List<AttributeData> result = new ArrayList<>();
        Map<AttributeName, Map<Class<? extends AttributeFlag>, AttributePath>> compositeGroups = new HashMap<>();

        // Collect all audit field paths
        for (Class<? extends AttributeFlag> flagType : flagTypes) {
            collectAuditFieldPaths(attributes, flagType, null, compositeGroups);
        }

        // Create CompositeAttributeData for grouped audit fields
        Set<AttributePath> processedPaths = new HashSet<>();
        for (var entry : compositeGroups.entrySet()) {
            var compositeName = entry.getKey();
            var auditFields = entry.getValue();

            if (auditFields.size() > 1) {
                // Multiple audit fields under the same composite - group them
                var builder = CompositeAttributeData.builder().name(compositeName);

                for (var flagEntry : auditFields.entrySet()) {
                    var flagType = flagEntry.getKey();
                    var path = flagEntry.getValue();
                    var leafName = getLeafAttributeName(path);

                    builder.attribute(createAuditData(leafName, flagType));
                    processedPaths.add(path);
                }

                result.add(builder.build());
            }
        }

        // Add individual audit fields that weren't part of composite groups
        for (Class<? extends AttributeFlag> flagType : flagTypes) {
            var path = findAttributePathWithFlag(attributes, flagType);
            if (path.isPresent() && !processedPaths.contains(path.get())) {
                var leafName = getLeafAttributeName(path.get());
                result.add(createAuditData(leafName, flagType));
            }
        }

        return result;
    }

    private static void collectAuditFieldPaths(List<Attribute> attributes, Class<? extends AttributeFlag> flagType,
            AttributeName parentComposite, Map<AttributeName, Map<Class<? extends AttributeFlag>, AttributePath>> compositeGroups) {

        for (Attribute attr : attributes) {
            if (attr.hasFlag(flagType)) {
                if (parentComposite != null) {
                    // This audit field is nested under a composite - group it
                    var path = new CompositeAttributePath(parentComposite, new SimpleAttributePath(attr.getName()));
                    compositeGroups.computeIfAbsent(parentComposite, k -> new HashMap<>())
                            .put(flagType, path);
                }
                // If parentComposite is null, this is a top-level audit field - will be handled individually
            } else if (attr instanceof CompositeAttribute comp) {
                // Recurse into composite attributes
                collectAuditFieldPaths(comp.getAttributes(), flagType, attr.getName(), compositeGroups);
            }
        }
    }

    private static Optional<AttributePath> findAttributePathWithFlag(List<Attribute> attributes, Class<? extends AttributeFlag> flagType) {
        for (Attribute attr : attributes) {
            if (attr.hasFlag(flagType)) {
                return Optional.of(new SimpleAttributePath(attr.getName()));
            } else if (attr instanceof CompositeAttribute comp) {
                var nestedPath = findAttributePathWithFlag(comp.getAttributes(), flagType);
                if (nestedPath.isPresent()) {
                    return nestedPath.map(path -> path.withPrefix(attr.getName()));
                }
            }
        }
        return Optional.empty();
    }

    private static AttributeName getLeafAttributeName(AttributePath path) {
        while (path.getRest() != null) {
            path = path.getRest();
        }
        return path.getFirst();
    }

    private static AttributeData createAuditData(AttributeName attributeName, Class<? extends AttributeFlag> flagType) {
        if (CreatorFlag.class.equals(flagType) || ModifierFlag.class.equals(flagType)) {
            // TODO: Get current user from security context when available
            // For now, return null which represents "system" user
            return new SimpleAttributeData<>(attributeName, null);
        } else if (CreatedDateFlag.class.equals(flagType) || ModifiedDateFlag.class.equals(flagType)) {
            return new SimpleAttributeData<>(attributeName, Instant.now());
        }
        throw new IllegalArgumentException("Unsupported audit flag type: " + flagType);
    }
}
