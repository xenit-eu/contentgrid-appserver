package com.contentgrid.appserver.query;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DummyEntityInstance extends HashMap<String, Object> implements EntityInstance {
    static EntityInstance fromMap(Map<String, ?> map) {
        var res = new DummyEntityInstance(map);
        if (map.get("id") == null) {
            res.put("id", UUID.randomUUID().toString());
        }
        return res;
    }

    private DummyEntityInstance(Map<String, ?> map) {
        super(map);
    }

    @Override
    public String getId() {
        return (String) this.get("id");
    }
}
