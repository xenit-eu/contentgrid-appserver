package com.contentgrid.appserver.query.engine.model;

import com.contentgrid.appserver.application.model.values.TableName;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class CGDelete {

    @NonNull
    TableName table;

    @Singular
    List<CGColumnFilter> conditions;
}
