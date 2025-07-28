package com.contentgrid.appserver.rest.hal.forms.property;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.hateoas.spring.affordances.property.BasicPropertyMetadata;
import java.net.URI;
import java.util.Map;
import lombok.Getter;
import org.springframework.core.ResolvableType;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions;

@Getter
public class RelationPropertyMetadata extends PropertyMetadataWithOptions {

    private final Application application;
    private final Relation relation;

    /**
     * @param relation The relation property
     */
    public RelationPropertyMetadata(Application application, Relation relation) {
        super(new BasicPropertyMetadata(relation.getSourceEndPoint().getName().getValue(), ResolvableType.forClass(URI.class))
                .withRequired(relation.getSourceEndPoint().isRequired())
                .withReadOnly(false));
        this.application = application;
        this.relation = relation;
    }

    @Override
    public HalFormsOptions getOptions() {
        return HalFormsOptions.remote(getTargetHref())
                .withMinItems(relation.getSourceEndPoint().isRequired() ? 1L : 0L)
                .withMaxItems(isManyTargets() ? null : 1L)
                // This is a JSON pointer into the item
                .withValueField("/_links/self/href");
    }

    private boolean isManyTargets() {
        return relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation;
    }

    private String getTargetHref() {
        return linkTo(methodOn(EntityRestController.class)
                .listEntity(application, relation.getTargetEndPoint().getEntity().getPathSegment(), 0, null, Map.of()))
                .toUri().toString();
    }
}
