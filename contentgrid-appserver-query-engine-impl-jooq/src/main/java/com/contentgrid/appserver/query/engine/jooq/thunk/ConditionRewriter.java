package com.contentgrid.appserver.query.engine.jooq.thunk;

import com.contentgrid.appserver.application.model.values.TableName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import lombok.NonNull;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.QueryPart;
import org.jooq.SQLDialect;
import org.jooq.VisitListener;
import org.jooq.impl.DSL;
import org.jooq.impl.QOM;

/**
 * Rewrites a {@link Condition} that is about to be nested inside a subquery, by lifting the conjuncts that do not
 * depend on that subquery out of it.
 * <p>
 * A conjunct is only correlated to the subquery when it references one of the aliases that the subquery brings in
 * scope. Every other conjunct can be evaluated by the enclosing query, which turns
 * {@code exists(select ... where root.foo = 'bar' and joined.baz = 'qux')} into
 * {@code root.foo = 'bar' and exists(select ... where joined.baz = 'qux')}. Both are equivalent, since
 * {@code exists(S where A and B) == B and exists(S where A)} when {@code B} does not depend on the rows of {@code S}.
 */
class ConditionRewriter {

    /**
     * Conditions are rendered to find the aliases they reference, the resulting SQL is discarded. The dialect is
     * irrelevant for that: an emulated expression still references the same fields.
     */
    private static final DSLContext RENDERER = DSL.using(SQLDialect.DEFAULT);

    @NonNull
    private final Set<TableName> scopedAliases;

    ConditionRewriter(@NonNull Collection<TableName> scopedAliases) {
        this.scopedAliases = Set.copyOf(scopedAliases);
    }

    /**
     * Rewrites a condition that is nested inside a subquery.
     *
     * @param condition the condition to rewrite
     * @param subquery builds the subquery condition out of the conjuncts that have to stay inside the subquery. It is
     * always called exactly once, and receives {@link DSL#noCondition()} when no conjunct is left for the subquery.
     * @return the subquery condition, conjoined with the conjuncts that were lifted out of the subquery
     */
    public Condition rewrite(@NonNull Condition condition, @NonNull UnaryOperator<Condition> subquery) {
        var split = split(condition);
        var subqueryCondition = subquery.apply(and(split.scoped()));

        if (split.lifted().isEmpty()) {
            return subqueryCondition;
        }
        return DSL.and(Stream.concat(split.lifted().stream(), Stream.of(subqueryCondition)).toList());
    }

    /**
     * Splits the top-level conjunction of a condition into the conjuncts that reference one of the scoped aliases, and
     * the conjuncts that can be evaluated without those aliases.
     * <p>
     * Conjuncts that reference no alias at all are kept in place, because lifting them out of the subquery gains
     * nothing.
     */
    public SplitCondition split(@NonNull Condition condition) {
        var scoped = new ArrayList<Condition>();
        var lifted = new ArrayList<Condition>();

        for (var conjunct : flatten(condition)) {
            var referenced = referencedAliases(conjunct);
            if (referenced.isEmpty() || !Collections.disjoint(referenced, scopedAliases)) {
                scoped.add(conjunct);
            } else {
                lifted.add(conjunct);
            }
        }

        return new SplitCondition(List.copyOf(scoped), List.copyOf(lifted));
    }

    /**
     * Finds all aliases that qualify a field anywhere inside a query part, including inside nested subqueries and
     * inside parts that are opaque to the jOOQ model API (a plain SQL template, for example).
     */
    public static Set<TableName> referencedAliases(@NonNull QueryPart part) {
        var aliases = new LinkedHashSet<TableName>();

        RENDERER.configuration()
                .derive(VisitListener.onVisitStart(context -> {
                    if (context.queryPart() instanceof Field<?> field) {
                        var name = field.getQualifiedName();
                        if (name.parts().length > 1) {
                            aliases.add(TableName.of(name.first()));
                        }
                    }
                }))
                .dsl()
                .render(part);

        return aliases;
    }

    /**
     * Flattens the conjuncts of a condition. {@link DSL#and(Collection)} combines them two by two, so the conjunction
     * has to be traversed recursively.
     */
    private static List<Condition> flatten(Condition condition) {
        if (condition instanceof QOM.And and) {
            return Stream.concat(flatten(and.$arg1()).stream(), flatten(and.$arg2()).stream()).toList();
        }
        return List.of(condition);
    }

    private static Condition and(List<Condition> conditions) {
        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }

    /**
     * @param scoped conjuncts that reference a scoped alias, and can only be evaluated inside the subquery
     * @param lifted conjuncts that can be evaluated outside of the subquery
     */
    public record SplitCondition(@NonNull List<Condition> scoped, @NonNull List<Condition> lifted) {

    }
}
