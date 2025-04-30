package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;
import java.util.Set;

public sealed interface Attribute permits CompositeAttribute, SimpleAttribute {

    AttributeName getName();

    String getDescription();

    List<ColumnName> getColumns();

    Set<AttributeFlag> getFlags();

}
