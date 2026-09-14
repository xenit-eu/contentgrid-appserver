package com.contentgrid.appserver.query.engine.jooq.thunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.jooq.JOOQUtils;
import java.util.List;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

class ConditionRewriterTest {

    private static final TableName ROOT = TableName.of("i0");
    private static final TableName JOINED = TableName.of("p1");
    private static final TableName OTHER_JOINED = TableName.of("p2");

    private static final ConditionRewriter REWRITER = new ConditionRewriter(List.of(JOINED, OTHER_JOINED));

    private static Field<String> field(TableName alias, String column) {
        return DSL.field(DSL.name(alias.getValue(), column), String.class);
    }

    @Test
    void referencedAliases_findsQualifiedFields() {
        var condition = field(ROOT, "number").eq(field(JOINED, "name"));

        assertThat(ConditionRewriter.referencedAliases(condition)).containsExactly(ROOT, JOINED);
    }

    @Test
    void referencedAliases_ignoresUnqualifiedFieldsAndValues() {
        var condition = DSL.field(DSL.name("number"), String.class).eq(DSL.value("foo"));

        assertThat(ConditionRewriter.referencedAliases(condition)).isEmpty();
    }

    @Test
    void referencedAliases_findsFieldsInsidePlainSQL() {
        var condition = JOOQUtils.normalize(field(JOINED, "name")).eq(DSL.value("foo"));

        assertThat(ConditionRewriter.referencedAliases(condition)).containsExactly(JOINED);
    }

    @Test
    void referencedAliases_findsFieldsInsideTSVectorSearch() {
        var condition = JOOQUtils.generateFTSCondition(field(JOINED, "name"), DSL.value("foo"), "english");

        assertThat(ConditionRewriter.referencedAliases(condition)).containsExactly(JOINED);
    }

    @Test
    void referencedAliases_findsFieldsInsideSubqueries() {
        var condition = DSL.exists(DSL.selectOne()
                .from(DSL.table(DSL.name("person")).as(OTHER_JOINED.getValue()))
                .where(field(OTHER_JOINED, "id").eq(field(ROOT, "customer"))));

        assertThat(ConditionRewriter.referencedAliases(condition)).containsExactly(OTHER_JOINED, ROOT);
    }

    @Test
    void split_liftsConditionsThatDoNotReferenceAScopedAlias() {
        var root = field(ROOT, "number").eq(DSL.value("foo"));
        var joined = field(JOINED, "name").eq(DSL.value("bar"));

        var split = REWRITER.split(DSL.and(root, joined));

        assertThat(split.lifted()).containsExactly(root);
        assertThat(split.scoped()).containsExactly(joined);
    }

    @Test
    void split_keepsConditionsWithoutAnyAlias() {
        var constant = DSL.condition(true);

        var split = REWRITER.split(constant);

        assertThat(split.lifted()).isEmpty();
        assertThat(split.scoped()).containsExactly(constant);
    }

    @Test
    void split_keepsConditionsThatReferenceBothScopedAndUnscopedAliases() {
        var mixed = field(ROOT, "number").eq(field(JOINED, "name"));

        var split = REWRITER.split(mixed);

        assertThat(split.lifted()).isEmpty();
        assertThat(split.scoped()).containsExactly(mixed);
    }

    @Test
    void split_flattensNestedConjunctions() {
        var root1 = field(ROOT, "number").eq(DSL.value("foo"));
        var root2 = field(ROOT, "amount").eq(DSL.value("bar"));
        var joined1 = field(JOINED, "name").eq(DSL.value("baz"));
        var joined2 = field(OTHER_JOINED, "name").eq(DSL.value("qux"));

        var split = REWRITER.split(DSL.and(root1, DSL.and(joined1, DSL.and(root2, joined2))));

        assertThat(split.lifted()).containsExactly(root1, root2);
        assertThat(split.scoped()).containsExactly(joined1, joined2);
    }

    @Test
    void split_doesNotSplitDisjunctions() {
        var root = field(ROOT, "number").eq(DSL.value("foo"));
        var joined = field(JOINED, "name").eq(DSL.value("bar"));
        var disjunction = DSL.or(root, joined);

        var split = REWRITER.split(disjunction);

        assertThat(split.lifted()).isEmpty();
        assertThat(split.scoped()).containsExactly(disjunction);
    }

    @Test
    void rewrite_movesLiftedConditionsOutOfTheSubquery() {
        var root = field(ROOT, "number").eq(DSL.value("foo"));
        var joined = field(JOINED, "name").eq(DSL.value("bar"));
        var joinCondition = joinCondition();

        var result = REWRITER.rewrite(DSL.and(root, joined),
                scoped -> DSL.exists(select().where(DSL.and(joinCondition, scoped))));

        assertThat(result).isEqualTo(DSL.and(root, DSL.exists(select().where(DSL.and(joinCondition, joined)))));
    }

    @Test
    void rewrite_withoutLiftedConditionsReturnsSubqueryOnly() {
        var joined = field(JOINED, "name").eq(DSL.value("bar"));
        var joinCondition = joinCondition();

        var result = REWRITER.rewrite(joined,
                scoped -> DSL.exists(select().where(DSL.and(joinCondition, scoped))));

        assertThat(result).isEqualTo(DSL.exists(select().where(DSL.and(joinCondition, joined))));
    }

    @Test
    void rewrite_withoutScopedConditionsKeepsSubqueryAsSemiJoin() {
        var root = field(ROOT, "number").eq(DSL.value("foo"));
        var joinCondition = joinCondition();

        var result = REWRITER.rewrite(root,
                scoped -> DSL.exists(select().where(DSL.and(joinCondition, scoped))));

        // DSL.noCondition() is the identity of DSL.and(), so only the join condition remains in the subquery
        assertThat(result).isEqualTo(DSL.and(root, DSL.exists(select().where(joinCondition))));
    }

    private static org.jooq.SelectJoinStep<?> select() {
        return DSL.selectOne().from(DSL.table(DSL.name("person")).as(JOINED.getValue()));
    }

    private static Condition joinCondition() {
        return DSL.field(DSL.name(JOINED.getValue(), "id"), UUID.class)
                .eq(DSL.field(DSL.name(ROOT.getValue(), "customer"), UUID.class));
    }
}
