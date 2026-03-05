package com.contentgrid.appserver.exception;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.SortableName;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public abstract class InvalidSortParameterException extends RuntimeException {

    public static class InvalidSortParameterFormatException extends InvalidSortParameterException {

        public InvalidSortParameterFormatException(@NonNull Throwable cause) {
            initCause(cause);
        }

        @Override
        public String getMessage() {
            return "The sort parameter has an invalid format: %s".formatted(getCause().getMessage());
        }
    }

    @RequiredArgsConstructor
    @Getter
    public static class InvalidSortParameterNameException extends InvalidSortParameterException {
        @NonNull
        private final EntityName entityName;

        @NonNull
        private final SortableName sortableName;

        @Override
        public String getMessage() {
            return "Sortable field %s was not found on entity %s".formatted(sortableName, entityName);
        }
    }

}