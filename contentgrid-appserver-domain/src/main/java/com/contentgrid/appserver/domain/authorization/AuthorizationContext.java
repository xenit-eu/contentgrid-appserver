package com.contentgrid.appserver.domain.authorization;

import com.contentgrid.appserver.domain.values.User;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import lombok.NonNull;

public record AuthorizationContext(
        @NonNull ThunkExpression<Boolean> predicate,
        User user
) {
    public static AuthorizationContext allowAll() {
        return allowAll(null);
    }

    public static AuthorizationContext allowAll(User user) {
        return new AuthorizationContext(Scalar.of(true), user);
    }

}
