package com.contentgrid.appserver.query.engine.jooq.util;

import java.util.ArrayList;
import java.util.List;
import org.jooq.Context;
import org.jooq.impl.CustomField;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public class CommaSeparatedListField extends CustomField<String> {
    private final List<String> values;

    public CommaSeparatedListField(List<String> values) {
        super("csv_list", SQLDataType.VARCHAR);
        this.values = new ArrayList<>(values);
    }

    @Override
    public void accept(Context<?> ctx) {
        if (values.isEmpty()) {
            ctx.visit(DSL.inline(""));
            return;
        }

        boolean first = true;
        for (String value : values) {
            if (!first) {
                ctx.sql(",");
            }
            ctx.visit(DSL.inline(value));
            first = false;
        }
    }
}
