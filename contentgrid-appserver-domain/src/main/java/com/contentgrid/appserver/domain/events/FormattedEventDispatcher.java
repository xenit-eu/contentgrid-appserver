package com.contentgrid.appserver.domain.events;

import com.fasterxml.jackson.core.TreeNode;

public interface FormattedEventDispatcher {
    void dispatchCreate(String entity, TreeNode newData);
    void dispatchUpdate(String entity, TreeNode oldData, TreeNode newData);
    void dispatchDelete(String entity, TreeNode oldData);
}
