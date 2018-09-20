package io.axoniq.axonserver.localstorage.query.expressions;

import io.axoniq.axonserver.localstorage.query.Expression;
import io.axoniq.axonserver.localstorage.query.ExpressionContext;
import io.axoniq.axonserver.localstorage.query.ExpressionResult;
import io.axoniq.axonserver.localstorage.query.PipeExpression;
import io.axoniq.axonserver.localstorage.query.Pipeline;
import io.axoniq.axonserver.localstorage.query.QueryResult;
import io.axoniq.axonserver.localstorage.query.result.BooleanExpressionResult;

/**
 * Author: marc
 */
public class OrExpression implements Expression, PipeExpression {

    private final String alias;
    private final Expression[] parameters;

    public OrExpression(String alias, Expression[] parameters) {
        this.alias = alias;
        this.parameters = parameters;
    }

    @Override
    public ExpressionResult apply(ExpressionContext context, ExpressionResult input) {
        for (Expression parameter : parameters) {
            if (parameter.apply(context, input).isTrue()) {
                return BooleanExpressionResult.TRUE;
            }
        }
        return BooleanExpressionResult.FALSE;
    }

    @Override
    public String alias() {
        return alias;
    }

    @Override
    public boolean process(ExpressionContext context, QueryResult value, Pipeline next) {
        if (apply(context, value.getValue()).isTrue()) {
            return next.process(value);
        }
        return true;
    }
}