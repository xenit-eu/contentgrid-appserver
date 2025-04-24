package com.contentgrid.appserver.rest.problem;

import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.UriTemplate;

@RequiredArgsConstructor
public class ProblemTypeUrlFactory {

    private final UriTemplate uriTemplate;

    public URI resolve(ProblemTypeResolvable problemType) {
        return uriTemplate.expand(List.of(problemType.getProblemHierarchy()));
    }

    public interface ProblemTypeResolvable {
        String[] getProblemHierarchy();
    }
}