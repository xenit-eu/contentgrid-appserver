package com.contentgrid.appserver.rest.problem;

import java.nio.charset.StandardCharsets;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;

public class ProblemTypeMessageSource extends ResourceBundleMessageSource {

    private static final ProblemTypeMessageSource INSTANCE = new ProblemTypeMessageSource();
    private static final MessageSourceAccessor ACCESSOR = new MessageSourceAccessor(INSTANCE);

    public ProblemTypeMessageSource() {
        setBasename("com.contentgrid.appserver.rest.problem.messages");
        setDefaultEncoding(StandardCharsets.UTF_8.toString());
    }

    public static MessageSourceAccessor getAccessor() {
        return ACCESSOR;
    }
}