package com.contentgrid.appserver.rest.exception;

import com.contentgrid.appserver.domain.data.transformers.InvalidPropertyDataException;
import com.contentgrid.appserver.exception.InvalidSortParameterException;
import com.contentgrid.appserver.rest.problem.ProblemFactory;
import com.contentgrid.appserver.rest.problem.ProblemType;
import com.contentgrid.appserver.rest.problem.ext.ConstraintViolationProblemProperties.FieldViolationProblemProperties;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.MessageSourceAccessor;
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

    @NonNull
    private final MessageSourceAccessor messageSourceAccessor;


    @ExceptionHandler({InvalidEntityDataException.class, JsonParseException.class})
    ResponseEntity<Problem> handleHttpMessageReadException(@NonNull Exception exception) {
        Throwable currentException = exception;

        while (currentException != null) {
            if (currentException instanceof InvalidEntityDataException invalidEntityDataException) {
                return handleMappingException(invalidEntityDataException);
            } else if (currentException instanceof JsonParseException parseException) {
                return handleJsonParseException(parseException);
            }
            currentException = currentException.getCause();
        }

        // Fallback handler: just a generic bad request
        return createResponse(
                problemFactory.createProblem(ProblemType.INVALID_REQUEST_BODY)
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withDetail(exception.getMessage())
        );
    }

    ResponseEntity<Problem> handleMappingException(InvalidEntityDataException exception) {
        var problem = problemFactory.createProblem(ProblemType.INVALID_REQUEST_BODY_TYPE)
                .withStatus(HttpStatus.BAD_REQUEST);
        log.warn("Invalid request body type:", exception);

        for (String attr : exception.getInvalidAttributes()) {
            problem = problem.withProperties(new FieldViolationProblemProperties(attr));
        }

        return createResponse(problem);
    }

    ResponseEntity<Problem> handleJsonParseException(JsonParseException exception) {
        log.warn("Invalid request body json:", exception);
        return createResponse(
                problemFactory.createProblem(ProblemType.INVALID_REQUEST_BODY_JSON)
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withDetail(formatJacksonError(exception))
        );
    }

    private static String formatJacksonError(JsonProcessingException exception) {
        var message = Objects.requireNonNullElse(exception.getOriginalMessage(), "No message");
        var location = exception.getLocation();
        if (location == null) {
            return message;
        }

        return message + " at " + location.offsetDescription();
    }

    @ExceptionHandler(InvalidSortParameterException.class)
    ResponseEntity<Problem> handleInvalidSortParameterException(@NonNull InvalidSortParameterException exception) {
        return createResponse(
                problemFactory.createProblem(ProblemType.INVALID_SORT_PARAMETER)
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withDetail(exception.getMessage())
        );
    }

    @ExceptionHandler(InvalidPropertyDataException.class)
    ResponseEntity<Problem> handleInvalidPropertyDataException(@NonNull InvalidPropertyDataException exception) {
        return createResponse(
                problemFactory.createProblem(ProblemType.INVALID_REQUEST_BODY_TYPE)
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withDetail(exception.getMessage())
                        .withProperties(Map.of(
                                "property-path", exception.getPath().toList(),
                                "all-errors", exception.allExceptions()
                                        .map(ex -> Map.of(
                                                "detail", ex.getMessage(),
                                                "property-path", ex.getPath().toList()
                                        ))
                                        .toList()
                        ))
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