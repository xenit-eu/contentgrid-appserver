package com.contentgrid.appserver.events;

import com.fasterxml.jackson.core.TreeNode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EntityChangeEventPayload {

    private static final String CREATE = "create";
    private static final String UPDATE = "update";
    private static final String DELETE = "delete";

    @Getter
    @NonNull
    private final String trigger;

    @Getter
    private final TreeNode old;

    private final TreeNode _new;

    public TreeNode getNew() {
        return _new;
    }

    public static EntityChangeEventPayload forCreate(@NonNull TreeNode newData) {
        return new EntityChangeEventPayload(CREATE, null, newData);
    }

    public static EntityChangeEventPayload forUpdate(@NonNull TreeNode oldData, @NonNull TreeNode newData) {
        return new EntityChangeEventPayload(UPDATE, oldData, newData);
    }

    public static EntityChangeEventPayload forDelete(@NonNull TreeNode oldData) {
        return new EntityChangeEventPayload(DELETE, oldData, null);
    }
}
