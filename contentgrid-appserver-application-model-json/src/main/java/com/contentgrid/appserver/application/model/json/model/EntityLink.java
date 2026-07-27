package com.contentgrid.appserver.application.model.json.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.net.URI;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntityLink {

    /**
     * The link relation type, will be placed as-is on the link object
     */
    @NonNull
    private URI rel;

    /**
     * Optional 'name' to be used for the link that will be placed as-is on the link object
     */
    private String name;

    /**
     * Optional reference to the owning attribute or relation, if the link is owned.
     * Will be used to fill in the <code>%{owner.*}</code> substitution variables
     */
    private List<PropertyPathElement> owner;

    /**
     * Optional reference to the internal attribute that stores the data (for links that can store data).
     * If omitted, this will be a plain link that always provides its {@link #fallbackTemplate} (which will be required in that case)
     */
    private List<PropertyPathElement> storage;

    /**
     * Optional 'profile' URI that will be placed as-is on the link object
     */
    private URI profile;

    /**
     * URI template that will be used when there is no stored data (or {@link #storage} is null).
     *
     * <ul>
     *     <li>{@link #storage} is null: Field is required and is always used to create the link 'href'</li>
     *     <li>{@link #storage} is set, and there is no stored data: Field is optional. If present, it is used to create the link 'href' else the href is set to the URL to the stored attribute</li>
     *     <li>{@link #storage} is set, and there is stored data: Field is ignored, link 'href' is set to the URL to the stored attribute</li>
     * </ul>
     */
    private UriTemplateDefinition fallbackTemplate;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UriTemplateDefinition {
        String automationSystem;
        String basePathName;
        @NonNull
        String template;
    }
}
