package com.contentgrid.appserver.rest.problem;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.domain.data.InvalidDataFormatException;
import com.contentgrid.appserver.domain.data.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.type.DataType;
import com.contentgrid.appserver.domain.data.validation.AllowedValuesConstraintViolationInvalidDataException;
import com.contentgrid.appserver.domain.data.validation.ContentMissingInvalidDataException;
import com.contentgrid.appserver.domain.data.validation.RegexPatternConstraintViolationInvalidDataException;
import com.contentgrid.appserver.domain.data.validation.RequiredConstraintViolationInvalidDataException;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorDecodeException;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.domain.values.version.ExactlyVersion;
import com.contentgrid.appserver.exception.InvalidFilterParameterException;
import com.contentgrid.appserver.exception.InvalidPaginationParameterException;
import com.contentgrid.appserver.exception.InvalidSortParameterException.InvalidSortParameterFormatException;
import com.contentgrid.appserver.exception.InvalidSortParameterException.InvalidSortParameterNameException;
import com.contentgrid.appserver.query.engine.api.exception.BlindRelationOverwriteException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.EntityLinkedByRequiredRelationException;
import com.contentgrid.appserver.query.engine.api.exception.PermissionDeniedException;
import com.contentgrid.appserver.query.engine.api.exception.RelationLinkNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.RequiredConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.UniqueConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.UnsatisfiedVersionException;
import com.contentgrid.appserver.rest.exception.EmptyRelationException;
import com.contentgrid.appserver.rest.exception.InvalidRelationTargetException;
import com.contentgrid.appserver.rest.exception.InvalidUriInListException;
import com.contentgrid.appserver.rest.exception.MissingRelationTargetException;
import com.contentgrid.appserver.rest.exception.MultipleRelationTargetsException;
import com.contentgrid.appserver.query.engine.api.exception.RelationTargetNotFoundException;
import com.contentgrid.appserver.rest.exception.ForbiddenRequestHeaderException;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactory;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.problem.ext.MergedProblemProperties;
import com.fasterxml.jackson.core.JsonParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.mediatype.problem.Problem;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@RequiredArgsConstructor
public class ProblemDetailsExceptionHandler {

    private final ProblemFactory problemFactory;

    static ResponseEntity<Problem> createResponse(Problem problem) {
        var responseBuilder = ResponseEntity.internalServerError();
        if (problem.getStatus() != null) {
            responseBuilder = ResponseEntity.status(problem.getStatus());
        }
        return responseBuilder.contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler
    ResponseEntity<Problem> inputValidation(InvalidPropertyDataException exception) {
        return createResponse(inputValidation(allExceptions(exception, InvalidPropertyDataException.class)
                .map(e -> {
                    Problem problem = switch(e.getCause()) {
                        case InvalidDataTypeException invalidDataType -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_INVALID_TYPE, invalidDataType.getExpectedType().getHumanDescription(), invalidDataType.getActualType().getHumanDescription())
                                .withProperties(Map.of(
                                    "expected_type", invalidDataType.getExpectedType().getTechnicalName(),
                                    "actual_type", invalidDataType.getActualType().getTechnicalName()
                                ));
                        case InvalidDataFormatException invalidDataFormat -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_INVALID_TYPE_FORMAT, invalidDataFormat.getExpectedType().getHumanDescription(),
                                        invalidDataFormat.getCause().getMessage())
                                .withProperties(Map.of(
                                    "expected_type", invalidDataFormat.getExpectedType().getTechnicalName(),
                                    "format_error", invalidDataFormat.getCause().getMessage()
                                ));
                        case ContentMissingInvalidDataException contentMissing -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_NO_CONTENT, contentMissing.getSubfield());
                        case RequiredConstraintViolationInvalidDataException requiredConstraintViolation -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_REQUIRED_VALUE);
                        case AllowedValuesConstraintViolationInvalidDataException allowedValuesConstraintViolation -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_ALLOWED_VALUES, allowedValuesConstraintViolation.getAllowedValues())
                                .withProperties(Map.of(
                                        "allowed_values", allowedValuesConstraintViolation.getAllowedValues()
                                ));
                        case RegexPatternConstraintViolationInvalidDataException regexPatternConstraintViolationInvalidDataException -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_PATTERN, regexPatternConstraintViolationInvalidDataException.getPattern().pattern())
                                .withProperties(Map.of(
                                        "pattern", regexPatternConstraintViolationInvalidDataException.getPattern().pattern()
                                ));
                        // All exception types should be covered above, this is a fallback for when there are additional
                        // exceptions added without adding a case.
                        default -> throw new IllegalArgumentException("Unsupported exception %s".formatted(e.getCause().getClass()));
                    };

                    return MergedProblemProperties.extend(problem, Map.of(
                            "field", String.join(".", e.getPath().toList())
                    ));
                })
                .toList()));
    }

    @ExceptionHandler
    ResponseEntity<Problem> inputValidation(UniqueConstraintViolationException exception, LinkFactoryProvider linkFactoryProvider) {
        return createResponse(inputValidation(allExceptions(exception, UniqueConstraintViolationException.class)
                .map(e -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_DUPLICATE_VALUE)
                        .withProperties(Map.of(
                                "field", String.join(".", e.getPropertyPath().toList()),
                                "conflicting_item", linkFactoryProvider.toItem(e.getConflictingEntity()).toUri().toString()
                        )))
                .toList()));
    }

    @ExceptionHandler
    ResponseEntity<Problem> inputValidation(RequiredConstraintViolationException exception) {
        return createResponse(inputValidation(allExceptions(exception, RequiredConstraintViolationException.class)
                .map(e -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_REQUIRED_VALUE)
                            .withProperties(Map.of(
                                    "field", String.join(".", e.getPropertyPath().toList())
                            )))
                .toList()));
    }

    @ExceptionHandler
    ResponseEntity<Problem> inputValidation(InvalidRelationTargetException exception, Application application, LinkFactoryProvider linkFactoryProvider) {
        return createResponse(inputValidation(allExceptions(exception, InvalidRelationTargetException.class)
                .map(e -> {
                    var relation = application.getRelationForEntity(e.getEntityName(), e.getRelationName()).orElseThrow();
                    var formatError = "Must match "+ linkFactoryProvider.itemMatcher(relation.getTargetEndPoint().getEntity()).getMatchingUriTemplates()
                            .stream()
                            .collect(Collectors.joining("' or '", "'", "'"));
                    return problemFactory.createProblem(
                                    ProblemType.INPUT_VALIDATION_INVALID_TYPE_FORMAT,
                                    DataType.of(relation).getHumanDescription(),
                                    formatError
                            )
                            .withProperties(Map.of(
                                    "field", e.getRelationName().getValue(),
                                    "expected_type", DataType.of(relation).getTechnicalName(),
                                    "format_error", formatError
                            ));
                })
                .toList()));
    }

    @ExceptionHandler
    ResponseEntity<Problem> inputValidation(RelationTargetNotFoundException exception, LinkFactoryProvider linkFactoryProvider) {
        return createResponse(inputValidation(allExceptions(exception, RelationTargetNotFoundException.class)
                .map(e -> {
                    var identity = EntityIdentity.forEntity(e.getEntityName(), e.getId());
                    return problemFactory.createProblem(ProblemType.INPUT_VALIDATION_MISSING_RELATION_TARGET, identity)
                            .withStatus(HttpStatus.BAD_REQUEST)
                            .withProperties(Map.of(
                                    "field", e.getRelation().getRelationName().getValue(),
                                    "missing_item", linkFactoryProvider.toItem(identity).toUri().toString()
                            ));
                })
                .toList()
        ));
    }

    Problem inputValidation(List<? extends Problem> problems) {
        return problemFactory.createProblem(ProblemType.INPUT_VALIDATION, problems.size())
                .withStatus(HttpStatus.BAD_REQUEST)
                .withProperties(Map.of(
                        "errors", problems
                ));
    }

    @ExceptionHandler
    ResponseEntity<Problem> queryParameter(InvalidFilterParameterException mainException) {
        return createResponse(multiProblem(InvalidFilterParameterException.class, exception ->
                problemFactory.createProblem(
                                ProblemType.INVALID_QUERY_PARAMETER_FILTER_FORMAT,
                                exception.getFilterName().getValue(),
                                DataType.of(exception.getType()).getHumanDescription()
                        )
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withProperties(Map.of(
                                "query_parameter", exception.getFilterName().getValue(),
                                "expected_type", DataType.of(exception.getType()).getTechnicalName(),
                                "format_error",  exception.getCause().getMessage()
                        ))
        ).apply(mainException));
    }

    @ExceptionHandler
    ResponseEntity<Problem> queryParameter(InvalidSortParameterFormatException exception) {
        return createResponse(problemFactory.createProblem(ProblemType.INVALID_QUERY_PARAMETER_SORT_FORMAT, exception.getCause().getMessage())
                .withStatus(HttpStatus.BAD_REQUEST)
                .withProperties(Map.of(
                        "query_parameter", "_sort",
                        "format_error", exception.getCause().getMessage()
                )));
    }

    @ExceptionHandler
    ResponseEntity<Problem> queryParameter(InvalidSortParameterNameException exception) {
        return createResponse(problemFactory.createProblem(
                        ProblemType.INVALID_QUERY_PARAMETER_SORT_TARGET,
                        exception.getSortableName().getValue(),
                        exception.getEntityName().getValue()
                )
                .withStatus(HttpStatus.BAD_REQUEST)
                .withProperties(Map.of(
                        "query_parameter", "_sort",
                        "target_name", exception.getSortableName().getValue()
                )));
    }

    @ExceptionHandler
    ResponseEntity<Problem> queryParameter(InvalidPaginationParameterException exception) {
        return createResponse(problemFactory.createProblem(ProblemType.INVALID_QUERY_PARAMETER_PAGINATION, exception.getParameter(), exception.getDetail())
                .withStatus(HttpStatus.BAD_REQUEST)
                .withProperties(Map.of(
                        "query_parameter", exception.getParameter(),
                        "format_error", exception.getDetail()
                )));
    }

    @ExceptionHandler
    ResponseEntity<Problem> queryParameter(CursorDecodeException exception) {
        return createResponse(problemFactory.createProblem(ProblemType.INVALID_QUERY_PARAMETER_PAGINATION, "_cursor", exception.getMessage())
                .withStatus(HttpStatus.BAD_REQUEST)
                .withProperties(Map.of(
                        "query_parameter", "_cursor",
                        "format_error", exception.getMessage()
                )));
    }

    @ExceptionHandler
    ResponseEntity<Problem> invalidRequest(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof JsonParseException ex) {
            // JsonParseException is sometimes the _cause_ of the HttpMessageNotReadableException,
            // but this handler always gets called first because resolution goes from root exception down the cause chain
            // So explicitly go down the necessary path here
            return invalidRequest(ex);
        }
        return createResponse(problemFactory.createProblem(ProblemType.INVALID_REQUEST_BODY)
                .withStatus(HttpStatus.BAD_REQUEST)
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> invalidRequest(JsonParseException exception) {
        var message = Objects.requireNonNullElse(exception.getOriginalMessage(), "No message");
        var location = exception.getLocation();
        if(location != null) {
            message += " at " + location.offsetDescription();
        }
        return createResponse(problemFactory.createProblem(ProblemType.INVALID_REQUEST_BODY_JSON)
                .withStatus(HttpStatus.BAD_REQUEST)
                .withDetail(message));
    }

    @ExceptionHandler
    ResponseEntity<Problem> invalidRequest(InvalidUriInListException exception) {
        return createResponse(problemFactory.createProblem(ProblemType.INVALID_REQUEST_BODY_URI_LIST)
                .withStatus(HttpStatus.BAD_REQUEST)
                .withDetail(exception.getMessage())
        );
    }

    @ExceptionHandler(exception = {MissingRelationTargetException.class, MultipleRelationTargetsException.class})
    ResponseEntity<Problem> invalidRequest() {
        return createResponse(problemFactory.createProblem(ProblemType.INVALID_REQUEST_BODY_SINGLE_LINK)
                .withStatus(HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler
    ResponseEntity<Problem> invalidRequest(MissingRequestHeaderException exception) {
        return createResponse(problemFactory.createProblem(
                        ProblemType.INVALID_REQUEST_REQUIRED_HEADER,
                        exception.getHeaderName()
                )
                .withStatus(HttpStatus.BAD_REQUEST)
                .withProperties(Map.of(
                        "header", exception.getHeaderName()
                )));
    }

    @ExceptionHandler
    ResponseEntity<Problem> invalidRequest(ForbiddenRequestHeaderException exception) {
        return createResponse(problemFactory.createProblem(
                        ProblemType.INVALID_REQUEST_FORBIDDEN_HEADER,
                        exception.getHeader()
                )
                .withStatus(HttpStatus.BAD_REQUEST)
                .withProperties(Map.of(
                        "header", exception.getHeader()
                )));
    }

    @ExceptionHandler
    ResponseEntity<Problem> versionConflict(UnsatisfiedVersionException exception) {
        return createResponse(problemFactory.createProblem(
                        ProblemType.UNSATISFIED_VERSION,
                        exception.getActualVersion(),
                        exception.getRequestedVersion()
                )
                .withStatus(HttpStatus.PRECONDITION_FAILED)
                .withProperties(properties -> {
                    if(exception.getActualVersion() instanceof ExactlyVersion exactlyVersion) {
                        properties.put("actual_version", exactlyVersion.getVersion());
                    }
                }));
    }

    @ExceptionHandler
    ResponseEntity<Problem> notFound(NoResourceFoundException exception) {
        return createResponse(problemFactory.createProblem(ProblemType.NOT_FOUND_ENDPOINT)
                .withStatus(HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler
    ResponseEntity<Problem> notFound(MethodArgumentTypeMismatchException exception) {
        // Only treat path variables as a 404
        if(exception.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return createResponse(problemFactory.createProblem(ProblemType.NOT_FOUND_ENDPOINT)
                    .withStatus(HttpStatus.NOT_FOUND));
        }

        // rethrow to let spring handle the rest
        throw exception;
    }

    @ExceptionHandler
    ResponseEntity<Problem> notFound(EntityIdNotFoundException exception) {
        return createResponse(problemFactory.createProblem(ProblemType.NOT_FOUND_ENTITY_ITEM, exception.getEntityName().getValue(), exception.getId().getValue())
                .withStatus(HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler({EmptyRelationException.class, RelationLinkNotFoundException.class})
    ResponseEntity<Problem> notFound() {
        return createResponse(problemFactory.createProblem(ProblemType.NOT_FOUND_RELATION_ITEM)
                .withStatus(HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler
    ResponseEntity<Problem> integrity(BlindRelationOverwriteException mainException, Application application, LinkFactoryProvider linkFactoryProvider) {
        return createResponse(multiProblem(BlindRelationOverwriteException.class, exception -> {
            var relation = application.getRelationForEntity(exception.getExistingRelation().getEntityName(),
                    exception.getExistingRelation().getRelationName()).orElseThrow();
            return problemFactory.createProblem(
                            ProblemType.INTEGRITY_RELATION_BLIND_OVERWRITE,
                            exception.getNewRelation(),
                            exception.getTargetEntity(),
                            exception.getExistingRelation()
                    )
                    .withStatus(HttpStatus.CONFLICT)
                    .withProperties(properties -> {
                        var newItemLink = linkFactoryProvider.toItem(EntityIdentity.forEntity(exception.getNewRelation().getEntityName(),
                                        exception.getNewRelation().getEntityId())).toUri();
                        var newRelationLink = linkFactoryProvider.toRelation(exception.getNewRelation()).orElseThrow()
                                .toUri();
                        properties.put("new_item", newItemLink.toString());
                        properties.put("new_relation", newRelationLink.toString());

                        var existingItemLink = linkFactoryProvider.toItem(exception.getExistingValue()).toUri();
                        var existingRelationLink = linkFactoryProvider.toRelation(exception.getExistingRelation())
                                .orElseThrow().toUri();
                        properties.put("existing_item", existingItemLink.toString());
                        properties.put("existing_relation", existingRelationLink.toString());

                        var targetItemLink = linkFactoryProvider.toItem(exception.getTargetEntity()).toUri();
                        properties.put("target_item", targetItemLink.toString());

                        if (relation.getTargetEndPoint().getName() != null) {
                            linkFactoryProvider.toRelation(
                                            RelationIdentity.forRelation(exception.getTargetEntity(),
                                                    relation.getTargetEndPoint()
                                                            .getName()))
                                    .map(LinkFactory::toUri)
                                    .ifPresent(uri -> properties.put("target_relation", uri.toString()));
                        }
                    });
        }).apply(mainException));
    }

    @ExceptionHandler
    ResponseEntity<Problem> integrity(EntityLinkedByRequiredRelationException exception, LinkFactoryProvider linkFactoryProvider) {
        return createResponse(problemFactory.createProblem(ProblemType.INTEGRITY_REQUIRED_RELATION, exception.getTargetRelationIdentity())
                .withStatus(HttpStatus.CONFLICT)
                .withProperties(Map.of(
                        "affected_relation", linkFactoryProvider.toRelation(exception.getTargetRelationIdentity()).orElseThrow().toUri().toString()
                )));
    }

    @ExceptionHandler
    ResponseEntity<?> permissionDenied(PermissionDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    private static <E extends Throwable> Stream<E> allExceptions(E e, Class<E> clazz) {
        return Stream.concat(
                Stream.of(e),
                Arrays.stream(e.getSuppressed())
                        .filter(clazz::isInstance)
                        .map(clazz::cast)
                        .flatMap(x -> allExceptions(x, clazz))
        );
    }

    private static <E extends Throwable> Function<E, ? extends Problem> multiProblem(Class<E> clazz, Function<E, ? extends Problem> convertor) {
        return mainException -> {
            var mainProblem = convertor.apply(mainException);
            var additionalProblems = allExceptions(mainException, clazz)
                    .filter(e -> e != mainException)
                    .map(convertor)
                    .toList();

            if (additionalProblems.isEmpty()) {
                return mainProblem;
            }

            return MergedProblemProperties.extend(mainProblem, Map.of("additional_errors", additionalProblems));
        };
    }
}
