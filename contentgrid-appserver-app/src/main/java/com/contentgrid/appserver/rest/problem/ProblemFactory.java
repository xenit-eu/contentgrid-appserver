package com.contentgrid.appserver.rest.problem;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.hateoas.mediatype.problem.Problem;

@RequiredArgsConstructor
public class ProblemFactory {
    private final MessageSourceAccessor messageSourceAccessor;
    private final ProblemTypeUrlFactory problemTypeUrlFactory;

    public Problem createProblem(ProblemType problemType, Object... arguments) {
        return Problem.create()
                .withType(problemTypeUrlFactory.resolve(problemType))
                .withTitle(resolveMessage(problemType.forTitle()))
                .withDetail(resolveMessage(problemType.forDetails(arguments)));
    }

    private String resolveMessage(MessageSourceResolvable resolvable) {
        var message = messageSourceAccessor.getMessage(resolvable);
        return message.isEmpty() ? null : message;
    }

}
