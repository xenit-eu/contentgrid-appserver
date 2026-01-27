package com.contentgrid.appserver.rest.problem;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.domain.data.InvalidDataException;
import com.contentgrid.appserver.domain.data.InvalidDataFormatException;
import com.contentgrid.appserver.domain.data.InvalidDataTypeException;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.validation.AllowedValuesConstraintViolationInvalidDataException;
import com.contentgrid.appserver.domain.data.validation.ContentMissingInvalidDataException;
import com.contentgrid.appserver.domain.data.validation.RequiredConstraintViolationInvalidDataException;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorDecodeException;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.domain.values.version.ExactlyVersion;
import com.contentgrid.appserver.exception.InvalidFilterParameterException;
import com.contentgrid.appserver.exception.InvalidPaginationParameterException;
import com.contentgrid.appserver.exception.InvalidSortParameterException;
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
import com.contentgrid.appserver.rest.exception.MultipartDataMissingContentTypeException;
import com.contentgrid.appserver.rest.exception.MultipleRelationTargetsException;
import com.contentgrid.appserver.rest.exception.RelationTargetNotFoundException;
import com.contentgrid.appserver.rest.links.factory.LinkFactory;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.problem.ext.ConstraintViolationProblemProperties;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.hateoas.mediatype.problem.Problem;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class ContentGridExceptionHandler {

    @NonNull
    private final ProblemFactory problemFactory;

    @NonNull
    private final MessageSourceAccessor messageSourceAccessor;


    @ExceptionHandler
    ResponseEntity<Problem> handleJsonParseException(@NonNull JsonParseException exception) {
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

    @ExceptionHandler
    ResponseEntity<Problem> handleUnsatisfiedVersionException(UnsatisfiedVersionException exception) {
        return createResponse(
                problemFactory.createProblem(ProblemType.UNSATISFIED_VERSION, exception.getActualVersion(), exception.getRequestedVersion())
                        .withStatus(HttpStatus.PRECONDITION_FAILED)
                        .withDetail(exception.getMessage())
                        .withProperties(properties -> {
                            if(exception.getActualVersion() instanceof ExactlyVersion exactlyVersion) {
                                properties.put("actual-version", exactlyVersion.getVersion());
                            }
                        })
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleBlindRelationOverwrite(BlindRelationOverwriteException exception, Application application, LinkFactoryProvider linkFactoryProvider) {
        var relation = application.getRelationForEntity(exception.getExistingRelation().getEntityName(), exception.getExistingRelation().getRelationName()).orElseThrow();

        var allErrors = allExceptions(exception, BlindRelationOverwriteException.class).toList();

        if (allErrors.size() > 1) {
            // If we have multiple relations that would be overwritten, put the whole collection behind the problem's "error" key
            var propertiesBuilder = ConstraintViolationProblemProperties.builder();
            for (var error : allErrors) {
                var problem = convertBlindRelationOverwrite(error, application, linkFactoryProvider);
                propertiesBuilder.target(
                        problem,
                        error.getTargetEntity().getEntityName().getValue(),
                        error.getTargetEntity().getEntityId().getValue().toString()
                );
            }

            var constraintViolationProblemProperties = propertiesBuilder.build();

            return createResponse(
                    problemFactory.createProblem(ProblemType.INTEGRITY_RELATION_BLIND_OVERWRITE,
                                    exception.getNewRelation(), exception.getTargetEntity(), exception.getExistingRelation())
                            .withStatus(HttpStatus.CONFLICT)
                            .withProperties(constraintViolationProblemProperties)
            );
        } else {
            return createResponse(
                    convertBlindRelationOverwrite(exception, application, linkFactoryProvider)
            );
        }
    }

    private Problem convertBlindRelationOverwrite(BlindRelationOverwriteException exception, Application application, LinkFactoryProvider linkFactoryProvider) {
        var relation = application.getRelationForEntity(exception.getExistingRelation().getEntityName(), exception.getExistingRelation().getRelationName()).orElseThrow();

        return problemFactory.createProblem(ProblemType.INTEGRITY_RELATION_BLIND_OVERWRITE, exception.getNewRelation(), exception.getTargetEntity(), exception.getExistingRelation())
                .withStatus(HttpStatus.CONFLICT)
                .withProperties(properties -> {
                    var newItemLink = linkFactoryProvider.toItem(EntityIdentity.forEntity(exception.getNewRelation().getEntityName(), exception.getNewRelation().getEntityId())).toUri();
                    var newRelationLink = linkFactoryProvider.toRelation(exception.getNewRelation()).orElseThrow().toUri();
                    properties.put("new-item", newItemLink.toString());
                    properties.put("new-relation", newRelationLink.toString());

                    var existingItemLink = linkFactoryProvider.toItem(exception.getExistingValue()).toUri();
                    var existingRelationLink = linkFactoryProvider.toRelation(exception.getExistingRelation()).orElseThrow().toUri();
                    properties.put("existing-item", existingItemLink.toString());
                    properties.put("existing-relation", existingRelationLink.toString());

                    var targetItemLink = linkFactoryProvider.toItem(exception.getTargetEntity()).toUri();
                    var targetRelationLink = Optional.<URI>empty();
                    if(relation.getTargetEndPoint().getName() != null) {
                        targetRelationLink = linkFactoryProvider.toRelation(RelationIdentity.forRelation(exception.getTargetEntity(), relation.getTargetEndPoint()
                                        .getName()))
                                .map(LinkFactory::toUri);
                    }
                    properties.put("target-item", targetItemLink.toString());
                    targetRelationLink.ifPresent(uri -> properties.put("target-relation", uri.toString()));
                });

    }

    @ExceptionHandler
    ResponseEntity<?> handlePermissionDeniedException(PermissionDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleInvalidFilterParameterException(@NonNull InvalidFilterParameterException exception) {
        return createResponse(
                problemFactory.createProblem(ProblemType.INVALID_FILTER_PARAMETER_FORMAT)
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withDetail(exception.getMessage())
                        .withProperties(Map.of(
                                "all-errors", allExceptions(exception, InvalidFilterParameterException.class)
                                        .map(ex -> Map.of(
                                                "detail", ex.getMessage(),
                                                "property", ex.getFilterName(),
                                                "invalid_value", ex.getValue()
                                        ))
                                        .toList(),
                                "property", exception.getFilterName(),
                                "invalid_value", exception.getValue()
                        ))
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleInvalidPaginationParameterException(@NonNull InvalidPaginationParameterException exception) {
        return createResponse(
                problemFactory.createProblem(ProblemType.INVALID_PAGINATION_PARAMETER)
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withDetail(exception.getMessage())
                        .withProperties(Map.of(
                                "query_parameter", exception.getParameter(),
                                "invalid_value", exception.getValue()
                        ))
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleInvalidSortParameterException(@NonNull InvalidSortParameterException exception) {
        return createResponse(
                problemFactory.createProblem(ProblemType.INVALID_SORT_PARAMETER)
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withDetail(exception.getMessage())
                        .withProperties(Map.of(
                                "all-errors", allExceptions(exception, InvalidSortParameterException.class)
                                        .map(ex -> Map.of("detail", ex.getMessage()))
                                        .toList()
                        ))
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleInvalidCursor(@NonNull CursorDecodeException e) {
        return createResponse(problemFactory.createProblem(ProblemType.INVALID_PAGINATION_PARAMETER)
                .withStatus(HttpStatus.BAD_REQUEST)
                .withDetail(e.getMessage()));
    }

    @ExceptionHandler
    // This includes handling for all subclasses of InvalidDataException, as those are always wrapped in InvalidPropertyDataException
    // before they end up here
    ResponseEntity<Problem> handleInvalidPropertyDataException(@NonNull InvalidPropertyDataException exception) {
        var allErrors = allExceptions(exception, InvalidPropertyDataException.class).toList();

        var propertiesBuilder = ConstraintViolationProblemProperties.builder();
        for (var error : allErrors) {
            propertiesBuilder.field(
                    handleInvalidDataException(error.getCause()),
                    String.join(".", error.getPath().toList())
            );
        }

        var validationProblemProperties = propertiesBuilder.build();

        return createResponse(
                problemFactory.createProblem(ProblemType.INPUT_VALIDATION, allErrors.size())
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withProperties(validationProblemProperties)
        );
    }


    private Problem handleInvalidDataException(InvalidDataException exception) {
        return switch (exception) {
            case InvalidDataTypeException invalidDataType -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_INVALID_TYPE, invalidDataType.getExpectedType().getHumanDescription(), invalidDataType.getActualType().getHumanDescription())
                    .withProperties(properties -> {
                        properties.put("expected-type", invalidDataType.getExpectedType().getTechnicalName());
                        properties.put("actual-type", invalidDataType.getActualType().getTechnicalName());
                    });
            case InvalidDataFormatException invalidDataFormat -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_INVALID_TYPE_FORMAT, invalidDataFormat.getExpectedType().getHumanDescription(),
                            exception.getCause().getMessage())
                    .withProperties(properties -> {
                        properties.put("expected-type", invalidDataFormat.getExpectedType().getTechnicalName());
                        properties.put("format-error", invalidDataFormat.getCause().getMessage());
                    });
            case ContentMissingInvalidDataException contentMissing -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_NO_CONTENT, contentMissing.getSubfield());
            case RequiredConstraintViolationInvalidDataException requiredConstraintViolation -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_REQUIRED_VALUE);
            case AllowedValuesConstraintViolationInvalidDataException allowedValuesConstraintViolation -> problemFactory.createProblem(ProblemType.INPUT_VALIDATION_ALLOWED_VALUES, allowedValuesConstraintViolation.getAllowedValues());
            case InvalidPropertyDataException invalidPropertyDataException -> handleInvalidPropertyDataException(invalidPropertyDataException).getBody();
            // All exception types should be covered above, this is a fallback for when there are additional
            // exceptions added without adding a case.
            default -> Problem.create()
                    .withDetail(exception.getMessage());
        };
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleUniqueConstraintViolation(@NonNull UniqueConstraintViolationException exception, LinkFactoryProvider linkFactoryProvider) {
        return createResponse(problemFactory.createProblem(ProblemType.INPUT_VALIDATION_DUPLICATE_VALUE, String.join(".", exception.getPropertyPath().toList()))
                .withStatus(HttpStatus.CONFLICT)
                .withProperties(properties -> {
                    var existingEntityLink = linkFactoryProvider.toItem(exception.getConflictingEntity());
                    properties.put("conflicting-item", existingEntityLink.toUri().toString());
                }));
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleRequiredConstraintViolation(@NonNull RequiredConstraintViolationException exception, LinkFactoryProvider linkFactoryProvider) {
        return createResponse(problemFactory.createProblem(ProblemType.INTEGRITY_REQUIRED, exception.getEntityIdentity(), String.join(".", exception.getPropertyPath().toList()))
                .withStatus(HttpStatus.BAD_REQUEST)
                .withProperties(properties -> {
                    properties.put("property", String.join(".", exception.getPropertyPath().toList()));
                })
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleEntityLinkedByRequiredRelation(@NonNull EntityLinkedByRequiredRelationException exception, LinkFactoryProvider linkFactoryProvider) {
        return createResponse(problemFactory.createProblem(ProblemType.INTEGRITY_RELATION_REQUIRED, exception.getTargetRelationIdentity())
                .withStatus(HttpStatus.CONFLICT)
                .withProperties(properties -> {
                    var affectedRelation = linkFactoryProvider.toRelation(exception.getTargetRelationIdentity()).orElseThrow().toUri();
                    properties.put("affected-relation", affectedRelation.toString());
                })
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleEntityItemNotFound(@NonNull EntityIdNotFoundException exception) {
        var id = exception.getId().getValue();
        var entity = exception.getEntityName().getValue();
        return createResponse(problemFactory.createProblem(ProblemType.NOT_FOUND_ENTITY_ITEM, entity, id)
                .withStatus(HttpStatus.NOT_FOUND)
                .withProperties(Map.of("id", id, "entity", entity))
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleRelationLinkNotFound(@NonNull RelationLinkNotFoundException exception) {
        var targetEntity = exception.getTargetEntity();
        var sourceRelationIdentity = exception.getSourceRelationIdentity();
        var relationName = sourceRelationIdentity.getRelationName();
        var id = EntityIdentity.forEntity(sourceRelationIdentity.getEntityName(), sourceRelationIdentity.getEntityId());

        return createResponse(problemFactory.createProblem(ProblemType.NOT_FOUND_RELATION_ITEM, relationName, id, targetEntity.getEntityId())
                .withStatus(HttpStatus.NOT_FOUND)
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleEmptyRelation(@NonNull EmptyRelationException exception) {
        var relationIdentity = exception.getRelationIdentity();
        var entityIdentity = EntityIdentity.forEntity(relationIdentity.getEntityName(), relationIdentity.getEntityId());

        return createResponse(problemFactory.createProblem(ProblemType.NOT_FOUND_EMPTY_RELATION, relationIdentity.getRelationName(), entityIdentity)
                .withStatus(HttpStatus.NOT_FOUND)
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleRelationTargetNotFound(@NonNull RelationTargetNotFoundException exception) {
        var allExceptions = exception.allExceptions().toList();

        var propertiesBuilder = ConstraintViolationProblemProperties.builder();
        for (var ex : allExceptions) {
            propertiesBuilder.target(
                    handleEntityItemNotFound(ex).getBody(),
                            ex.getEntityName().getValue(),
                            ex.getId().getValue().toString()
            );
        }

        var validationProblemProperties = propertiesBuilder.build();

        return createResponse(
                problemFactory.createProblem(ProblemType.INTEGRITY_INVALID_RELATION_TARGET)
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withProperties(validationProblemProperties)
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleInvalidRelationTarget(@NonNull InvalidRelationTargetException exception) {
        return createResponse(
                problemFactory.createProblem(ProblemType.INTEGRITY_INVALID_RELATION_TARGET)
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withProperties(properties -> properties.put("reference", exception.getReference()))
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleInvalidUriInList(@NonNull InvalidUriInListException exception) {
        return createResponse(
                problemFactory.createProblem(ProblemType.INTEGRITY_INVALID_RELATION_TARGET)
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withDetail(exception.getMessage())
                        .withProperties(properties -> properties.put("reference", exception.getInvalid()))
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleResourceNotFound(@NonNull NoResourceFoundException exception) {
        return createResponse(problemFactory.createProblem(ProblemType.NOT_FOUND_ENTITY_DEFINITION, exception.getResourcePath())
                .withStatus(HttpStatus.NOT_FOUND)
                .withProperties(Map.of("resource", exception.getResourcePath()))
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Problem> handlePathVariableMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {

        // Only treat path variables as 404
        if (exception.getParameter() != null && exception.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return createResponse(
                    problemFactory.createProblem(ProblemType.NOT_FOUND_ENTITY_DEFINITION, request.getRequestURI())
                            .withStatus(HttpStatus.NOT_FOUND)
                            .withProperties(Map.of("resource", request.getRequestURI()))
            );
        }

        // Let Spring handle everything else
        throw exception;
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleMissingRequestHeader(MissingRequestHeaderException exception) {
        return createResponse(problemFactory.createProblem(ProblemType.MISSING_REQUIRED_HEADER, exception.getHeaderName())
                .withStatus(HttpStatus.BAD_REQUEST)
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleMissingContentType(MultipartDataMissingContentTypeException exception) {
        return createResponse(problemFactory.createProblem(ProblemType.MISSING_CONTENT_TYPE, exception.getFieldName())
                .withStatus(HttpStatus.BAD_REQUEST)
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleMissingRelationTarget(@NonNull MissingRelationTargetException exception) {
        return createResponse(problemFactory.createProblem(ProblemType.INVALID_REQUEST_BODY)
                .withStatus(HttpStatus.BAD_REQUEST)
                .withDetail("No relation targets provided in request body")
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleMultipleRelationTargets(@NonNull MultipleRelationTargetsException exception) {
        return createResponse(problemFactory.createProblem(ProblemType.INVALID_REQUEST_BODY)
                .withStatus(HttpStatus.BAD_REQUEST)
                .withDetail("Multiple relation targets provided for to-one relation")
        );
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleUnsupportedMediaType(@NonNull HttpMediaTypeNotSupportedException exception) {
        var maybeContentType = Optional.ofNullable(exception.getContentType());
        var contentType = maybeContentType.map(MimeType::toString).orElse("unknown");

        return createResponse(problemFactory.createProblem(ProblemType.UNSUPPORTED_CONTENT_TYPE, contentType)
                .withStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .withProperties(properties -> {
                    if (maybeContentType.isPresent()) {
                        properties.put("content-type", contentType);
                    }
                    var supportedTypes = exception.getSupportedMediaTypes().stream()
                            .map(org.springframework.http.MediaType::toString)
                            .toList();
                    if (!supportedTypes.isEmpty()) {
                        properties.put("supported-types", supportedTypes);
                    }
                })
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

    private static <E extends Throwable> Stream<E> allExceptions(E e, Class<E> clazz) {
        return Stream.concat(
                Stream.of(e),
                Arrays.stream(e.getSuppressed())
                        .filter(clazz::isInstance)
                        .map(clazz::cast)
                        .flatMap(x -> allExceptions(x, clazz))
        );

    }

}
