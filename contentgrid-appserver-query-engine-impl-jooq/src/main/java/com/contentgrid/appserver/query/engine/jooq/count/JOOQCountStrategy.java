package com.contentgrid.appserver.query.engine.jooq.count;

import com.contentgrid.appserver.domain.values.ItemCount;
import org.jooq.DSLContext;
import org.jooq.Select;

public interface JOOQCountStrategy {

    ItemCount count(DSLContext dslContext, Select<?> query);

}
