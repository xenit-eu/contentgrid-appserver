package com.contentgrid.appserver.application.model.attributes;

import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;

public interface Attribute {

    AttributeName getName();

    List<ColumnName> getColumns();

}
