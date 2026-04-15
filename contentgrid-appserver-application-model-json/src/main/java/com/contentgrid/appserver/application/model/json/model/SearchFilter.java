package com.contentgrid.appserver.application.model.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import java.util.Locale;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchFilter {

    @NonNull
    private String name;

    @NonNull
    private List<PropertyPathElement> attributePath;

    @JsonInclude(value = Include.CUSTOM, valueFilter = Translations.EmptyTranslation.class)
    private Translations title;
    @JsonInclude(value = Include.CUSTOM, valueFilter = Translations.EmptyTranslation.class)
    private Translations description;

    @NonNull
    private String type; // exact, prefix, ...

    private Locale locale;

    @JsonInclude(Include.NON_EMPTY)
    private List<String> flags;
}
