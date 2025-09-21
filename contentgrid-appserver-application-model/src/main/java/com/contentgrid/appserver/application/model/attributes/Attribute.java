package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.attributes.Attribute.AttributeTranslations;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.IgnoredFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.i18n.ManipulatableTranslatable;
import com.contentgrid.appserver.application.model.i18n.Translatable;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.With;

public sealed interface Attribute extends Translatable<AttributeTranslations> permits CompositeAttribute, SimpleAttribute {

    @Value
    @NoArgsConstructor(access = AccessLevel.PACKAGE, force = true)
    @AllArgsConstructor
    @With
    class AttributeTranslations {
        String name;

        String description;
    }

    AttributeName getName();

    @Deprecated(forRemoval = true)
    default String getDescription() {
        return getTranslations(Locale.ROOT).getDescription();
    }

    List<ColumnName> getColumns();

    Set<AttributeFlag> getFlags();

    /**
     * Returns whether this attribute has a flag of the specified type.
     *
     * @param flagClass the class object representing the flag type
     * @return whether this attribute has the flag
     */
    default boolean hasFlag(Class<? extends AttributeFlag> flagClass) {
        return getFlags().stream().anyMatch(flagClass::isInstance);
    }

    /**
     * Returns whether this attribute is ignored in request and response bodies.
     * @return whether this attribute is ignored
     */
    default boolean isIgnored() {
        return hasFlag(IgnoredFlag.class);
    }

    /**
     * Returns whether this attribute is read-only.
     * @return whether this attribute is read-only
     */
    default boolean isReadOnly() {
        return hasFlag(ReadOnlyFlag.class);
    }

}
