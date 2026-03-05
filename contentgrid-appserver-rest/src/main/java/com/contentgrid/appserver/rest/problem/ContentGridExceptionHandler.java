package com.contentgrid.appserver.rest.problem;

import com.contentgrid.appserver.query.engine.api.exception.PermissionDeniedException;
import com.contentgrid.appserver.rest.exception.InvalidUriInListException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.hateoas.mediatype.problem.Problem;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class ContentGridExceptionHandler {

    @NonNull
    private final ProblemFactory problemFactory;

    @ExceptionHandler
    ResponseEntity<?> handlePermissionDeniedException(PermissionDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleInvalidUriInList(@NonNull InvalidUriInListException exception) {
        return createResponse(
                problemFactory.createProblem(ProblemType.INPUT_VALIDATION_INVALID_TYPE_FORMAT)
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withDetail(exception.getMessage())
                        .withProperties(properties -> properties.put("reference", exception.getInvalid()))
        );
    }

    static ResponseEntity<Problem> createResponse(Problem problem) {
        var responseBuilder = ResponseEntity.internalServerError();
        if (problem.getStatus() != null) {
            responseBuilder = ResponseEntity.status(problem.getStatus());
        }

        return responseBuilder.contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

}
