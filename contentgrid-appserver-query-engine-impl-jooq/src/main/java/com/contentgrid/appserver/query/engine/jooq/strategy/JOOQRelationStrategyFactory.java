package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.query.engine.api.exception.InvalidDataException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JOOQRelationStrategyFactory {

    public static <R extends Relation> JOOQXToOneRelationStrategy<R> forToOneRelation(R relation) {
        return (JOOQXToOneRelationStrategy<R>) switch (relation) {
            case SourceOneToOneRelation ignored -> new JOOQSourceOneToOneRelationStrategy();
            case ManyToOneRelation ignored -> new JOOQManyToOneRelationStrategy();
            case TargetOneToOneRelation ignored -> new JOOQTargetOneToOneRelationStrategy();
            default -> throw new InvalidDataException("Relation '%s' is not a one-to-one or many-to-one relation".formatted(relation.getSourceEndPoint().getName()));
        };
    }

    public static <R extends Relation> JOOQXToManyRelationStrategy<R> forToManyRelation(R relation) {
        return (JOOQXToManyRelationStrategy<R>) switch (relation) {
            case OneToManyRelation ignored -> new JOOQOneToManyRelationStrategy();
            case ManyToManyRelation ignored -> new JOOQManyToManyRelationStrategy();
            default -> throw new InvalidDataException("Relation '%s' is not a one-to-many or many-to-many relation".formatted(relation.getSourceEndPoint().getName()));
        };
    }

    public static <R extends Relation> JOOQRelationStrategy<R> forRelation(R relation) {
        return (JOOQRelationStrategy<R>) switch (relation) {
            case SourceOneToOneRelation ignored -> new JOOQSourceOneToOneRelationStrategy();
            case ManyToOneRelation ignored -> new JOOQManyToOneRelationStrategy();
            case TargetOneToOneRelation ignored -> new JOOQTargetOneToOneRelationStrategy();
            case OneToManyRelation ignored -> new JOOQOneToManyRelationStrategy();
            case ManyToManyRelation ignored -> new JOOQManyToManyRelationStrategy();
        };
    }
}
