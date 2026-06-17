package com.contentgrid.appserver.rest.automations;

import com.contentgrid.appserver.domain.automations.AutomationsModel.AutomationModel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;
import org.springframework.lang.Nullable;

@Data
@Builder(toBuilder = true, access = AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
@Relation(collectionRelation = IanaLinkRelations.ITEM_VALUE)
public class AutomationRepresentationModel extends RepresentationModel<AutomationRepresentationModel> {

    @NonNull
    private final String id;

    @NonNull
    private final String system;

    @NonNull
    private final String name;

    @Nullable
    @JsonInclude(Include.NON_NULL)
    private final Map<String, Object> data;

    @JsonIgnore
    private final CollectionModel<AutomationAnnotationRepresentationModel> annotations;

    @JsonInclude(Include.NON_NULL)
    @JsonUnwrapped
    @JsonProperty
    @Nullable
    private CollectionModel<AutomationAnnotationRepresentationModel> getAnnotations() {
        if (annotations == null) {
            return null;
        }
        return new CollectionModel<>(annotations) {
            /**
             * Overriding this to make sure that the marker link added to signal the need for curie-ing is added to the
             * outer representation model.
             * <p>
             * Copied from {@code org.springframework.hateoas.mediatype.hal.HalModelBuilder.HalRepresentationModel}
             *
             * @see <a href=https://github.com/spring-projects/spring-hateoas/commit/08bc96493e074f345566522216594df48db380a9>https://github.com/spring-projects/spring-hateoas/commit/08bc96493e074f345566522216594df48db380a9</a>
             */
            @Override
            public CollectionModel<AutomationAnnotationRepresentationModel> add(Link link) {
                AutomationRepresentationModel.this.add(link);
                return this;
            }
        };
    }

    public static AutomationRepresentationModel from(AutomationModel automation) {
        return AutomationRepresentationModel.builder()
                .id(automation.getId())
                .system(automation.getSystem())
                .name(automation.getName())
                .build();
    }

    public static AutomationRepresentationModel expandedFrom(AutomationModel automation, CollectionModel<AutomationAnnotationRepresentationModel> annotations) {
        return AutomationRepresentationModel.builder()
                .id(automation.getId())
                .system(automation.getSystem())
                .name(automation.getName())
                .data(automation.getData())
                .annotations(annotations)
                .build();
    }
}
