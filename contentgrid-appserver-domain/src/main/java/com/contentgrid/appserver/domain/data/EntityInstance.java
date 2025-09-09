package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import java.util.SequencedMap;

public interface EntityInstance {

    EntityIdentity getIdentity();

    SequencedMap<String, PlainDataEntry> getData();
}
