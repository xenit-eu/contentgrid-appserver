package com.contentgrid.appserver.domain.authorization;

import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import lombok.NonNull;

public record PermissionPredicate(
        @NonNull ThunkExpression<Boolean> predicate
) {
    public static PermissionPredicate allowAll() {
        return new PermissionPredicate(Scalar.of(true));
    }

}
