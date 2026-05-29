package com.contentgrid.appserver.rest.profile.assembler.hal;

import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.BaseAttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.FullTextSearchAttributeSearchFilter;
import com.contentgrid.appserver.rest.profile.assembler.BlueprintLinkRelations;
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
        FULL_TEXT("full-text"),
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

        public static ProfileSearchParamType from(BaseAttributeSearchFilter filter) {
            // Locales are not relevant for the profile (at the moment?), so we treat both filter types the same way here.
            if (filter instanceof FullTextSearchAttributeSearchFilter) return FULL_TEXT;

            AttributeSearchFilter attributeSearchFilter = (AttributeSearchFilter) filter;
            return switch (attributeSearchFilter.getOperation()) {
                case EXACT -> EXACT;
                case PREFIX -> PREFIX;
                case GREATER_THAN -> GREATER_THAN;
                case GREATER_THAN_OR_EQUAL -> GREATER_THAN_OR_EQUAL;
                case LESS_THAN -> LESS_THAN;
                case LESS_THAN_OR_EQUAL -> LESS_THAN_OR_EQUAL;
            };
        }
    }

}
