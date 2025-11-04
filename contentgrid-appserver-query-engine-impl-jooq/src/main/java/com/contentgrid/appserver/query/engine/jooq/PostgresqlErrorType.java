package com.contentgrid.appserver.query.engine.jooq;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jooq.exception.DataAccessException;

@RequiredArgsConstructor
public enum PostgresqlErrorType {
    // For SQLState codes, see https://www.postgresql.org/docs/current/errcodes-appendix.html
    UNKNOWN(Set.of(""), null),
    QUERY_TIMEOUT(Set.of("57014"), null),
    CONSTRAINT_VIOLATION(Set.of("23"), null),
    NOT_NULL_CONSTRAINT_VIOLATION(Set.of("23502"), CONSTRAINT_VIOLATION),
    FOREIGN_KEY_CONSTRAINT_VIOLATION(Set.of("23503"), CONSTRAINT_VIOLATION),
    UNIQUE_CONSTRAINT_VIOLATION(Set.of("23505"), CONSTRAINT_VIOLATION),
    ;

    @NonNull
    private final Set<String> sqlStates;
    private final PostgresqlErrorType parent;

    private static final Map<String, PostgresqlErrorType> ERROR_MAPPING;

    static {
        var errorMapping = new HashMap<String, PostgresqlErrorType>();
        for (var errorType : values()) {
            for (var sqlState : errorType.sqlStates) {
                errorMapping.put(sqlState, errorType);
            }
        }
        ERROR_MAPPING = Collections.unmodifiableMap(errorMapping);
    }

    public static PostgresqlErrorType from(@NonNull SQLException exception) {
        return fromSqlState(exception.getSQLState());
    }

    public static PostgresqlErrorType from(@NonNull DataAccessException exception) {
        return fromSqlState(exception.sqlState());
    }

    private static PostgresqlErrorType fromSqlState(@NonNull String sqlState) {
        for(int i = sqlState.length(); i > 0; i--) {
            var match = ERROR_MAPPING.get(sqlState.substring(0, i));
            if(match != null) {
                return match;
            }
        }
        return UNKNOWN;
    }

    public boolean is(@NonNull PostgresqlErrorType errorType) {
        var self = this;
        do {
            if(self == errorType) {
                return true;
            }
            self = self.parent;
        } while(self != null);
        return false;
    }
}
