package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.IgnoredFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;
import java.util.Set;

public sealed interface Attribute permits CompositeAttribute, SimpleAttribute {

    AttributeName getName();

    String getDescription();

    List<ColumnName> getColumns();

    Set<AttributeFlag> getFlags();

    default boolean hasFlag(Class<? extends AttributeFlag> flagClass) {
        return getFlags().stream().anyMatch(flagClass::isInstance);
    }

    default boolean isIgnored() {
        return hasFlag(IgnoredFlag.class);
    }

    default boolean isReadOnly() {
        return hasFlag(ReadOnlyFlag.class);
    }

}
