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
import com.contentgrid.appserver.exception.InvalidSortParameterException;
import com.contentgrid.appserver.query.engine.api.exception.BlindRelationOverwriteException;
import com.contentgrid.appserver.query.engine.api.exception.EntityLinkedByRequiredRelationException;
import com.contentgrid.appserver.query.engine.api.exception.PermissionDeniedException;
import com.contentgrid.appserver.query.engine.api.exception.RequiredConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.UniqueConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.UnsatisfiedVersionException;
import com.contentgrid.appserver.rest.links.factory.LinkFactory;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.problem.ext.ConstraintViolationProblemProperties;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

        return createResponse(
                problemFactory.createProblem(ProblemType.INTEGRITY_RELATION_BLIND_OVERWRITE, exception.getNewRelation(), exception.getTargetEntity(), exception.getExistingRelation())
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
                        })
        );
    }

    @ExceptionHandler
    ResponseEntity<?> handlePermissionDeniedException(PermissionDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler
    ResponseEntity<Problem> handleInvalidSortParameterException(@NonNull InvalidSortParameterException exception) {
        return createResponse(
                problemFactory.createProblem(ProblemType.INVALID_SORT_PARAMETER)
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .withDetail(exception.getMessage())
                        .withProperties(Map.of(
                                "all-errors", exception.allExceptions()
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
        var allErrors = exception.allExceptions().toList();

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


    static ResponseEntity<Problem> createResponse(Problem problem) {
        var responseBuilder = ResponseEntity.internalServerError();
        if (problem.getStatus() != null) {
            responseBuilder = ResponseEntity.status(problem.getStatus());
        }

        return responseBuilder.contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

}