package com.contentgrid.appserver.query.engine.jooq.test.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import lombok.NonNull;
import org.slf4j.LoggerFactory;

public class LoggerContext implements AutoCloseable {

    private final Level originalLevel;
    private final Logger logger;

    private LoggerContext(@NonNull Logger logger) {
        this.logger = logger;
        this.originalLevel = logger.getLevel();
    }

    public void setLevel(Level level) {
        logger.setLevel(level);
    }

    @Override
    public void close() {
        logger.setLevel(originalLevel);
    }

    public static LoggerContext create(@NonNull Class<?> loggerClass) {
        return new LoggerContext((Logger) LoggerFactory.getLogger(loggerClass));
    }
}
