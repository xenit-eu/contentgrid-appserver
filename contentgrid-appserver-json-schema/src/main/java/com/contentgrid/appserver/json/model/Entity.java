package com.contentgrid.appserver.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Entity {

    @NonNull
    private String name;

    @JsonInclude(value = Include.CUSTOM, valueFilter = Translations.EmptyTranslation.class)
    private Translations title;
    @JsonInclude(value = Include.CUSTOM, valueFilter = Translations.EmptyTranslation.class)
    private Translations collectionTitle;
    @JsonInclude(value = Include.CUSTOM, valueFilter = Translations.EmptyTranslation.class)
    private Translations description;

    @NonNull
    private String table;

    @NonNull
    private String pathSegment;

    @NonNull
    private String linkName;

    @NonNull
    private SimpleAttribute primaryKey;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Attribute> attributes;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<SearchFilter> searchFilters;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<SortableField> sortableFields;
}
