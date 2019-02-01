package io.axoniq.axonserver.localstorage.query.expressions.binary;

import io.axoniq.axonserver.localstorage.query.Expression;
import io.axoniq.axonserver.localstorage.query.ExpressionResult;

import java.util.Objects;

/**
 * @author Marc Gathier
 */
public class GtExpression extends AbstractBooleanExpression {

    public GtExpression(String alias, Expression[] parameters) {
        super(alias, parameters);
    }

    @Override
    protected boolean doEvaluate(ExpressionResult first, ExpressionResult second) {
        if (first == null && second != null) {
            return true;
        } else if (first != null && second == null) {
            return false;
        }
        return Objects.compare(first, second, ExpressionResult::compareTo) > 0;
    }
}
