package com.contentgrid.appserver.query.engine.api.thunx.expression;

import com.contentgrid.thunx.predicates.model.FunctionExpression;

public sealed interface CustomFunctionExpression<T> extends FunctionExpression<T> permits StringComparison, StringFunctionExpression {

    @Override
    default Operator getOperator() {
        return Operator.CUSTOM;
    }
}
