package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;

public sealed interface Attribute permits CompositeAttribute, ContentAttribute, SimpleAttribute, UserAttribute {

    AttributeName getName();

    List<ColumnName> getColumns();

    List<AttributeFlag> getFlags();

}
