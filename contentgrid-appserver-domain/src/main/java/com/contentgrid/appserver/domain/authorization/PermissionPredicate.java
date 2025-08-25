package com.contentgrid.appserver.domain.authorization;

import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.ThunkExpression;

public record PermissionPredicate(
        ThunkExpression<Boolean> predicate
) {
    public static PermissionPredicate allowAll() {
        return new PermissionPredicate(Scalar.of(true));
    }

}
