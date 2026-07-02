package com.contentgrid.appserver.domain.uritemplate;

import lombok.Getter;

@Getter
public class InvalidUriTemplateException extends Exception {

    private final String template;
    private final int position;
    private final String description;

    public InvalidUriTemplateException(String uriTemplate, int position, String message) {
        this.template = uriTemplate;
        this.position = position;
        this.description = message;
    }

    @Override
    public String getMessage() {
        var sb = new StringBuilder();
        sb.append("Invalid URI template at position ").append(position)
                .append(": ")
                .append(description)
                .append(System.lineSeparator());
        sb.append(template).append(System.lineSeparator());
        for (int i = 0; i < position; i++) {
            sb.append(' ');
        }
        sb.append('^');
        return sb.toString();
    }
}
