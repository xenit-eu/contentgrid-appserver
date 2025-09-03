package com.contentgrid.appserver.rest.assembler.profile.hal;

import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.GreaterThanOrEqualsSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.GreaterThanSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.LessThanOrEqualsSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.LessThanSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.PrefixSearchFilter;
import com.contentgrid.appserver.rest.assembler.profile.BlueprintLinkRelations;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.hateoas.server.core.Relation;

@Builder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@Relation(BlueprintLinkRelations.SEARCH_PARAM_STRING)
public class ProfileSearchParamRepresentationModel {

    String name;
    @JsonInclude(Include.NON_EMPTY)
    String title;

    @JsonIgnore
    ProfileSearchParamType type;

    @JsonProperty
    public String getType() {
        return type == null ? null : type.toString();
    }

    @Getter
    public enum ProfileSearchParamType {
        EXACT("exact-match"),
        PREFIX("prefix-match"),
        LESS_THAN("less-than"),
        LESS_THAN_OR_EQUAL("less-than-or-equal"),
        GREATER_THAN("greater-than"),
        GREATER_THAN_OR_EQUAL("greater-than-or-equal");

        private final String value;

        ProfileSearchParamType(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static ProfileSearchParamType from(AttributeSearchFilter filter) {
            return switch (filter) {
                case ExactSearchFilter ignored -> EXACT;
                case GreaterThanSearchFilter ignored -> GREATER_THAN;
                case GreaterThanOrEqualsSearchFilter ignored -> GREATER_THAN_OR_EQUAL;
                case LessThanSearchFilter ignored -> LESS_THAN;
                case LessThanOrEqualsSearchFilter ignored -> LESS_THAN_OR_EQUAL;
                case PrefixSearchFilter ignored -> PREFIX;
                default -> throw new UnsupportedOperationException("Unsupported value: " + filter.getClass());
            };
        }
    }

}
