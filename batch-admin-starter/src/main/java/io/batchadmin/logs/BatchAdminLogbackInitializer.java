package io.batchadmin.logs;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.batchadmin.autoconfigure.BatchAdminProperties;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Attaches the {@link LogbackJobLogAppender} to the Logback root logger on startup and detaches it
 * on shutdown. Activated only when Logback is the logging backend; otherwise per-execution log
 * capture is simply unavailable.
 */
public class BatchAdminLogbackInitializer implements InitializingBean, DisposableBean {

    private static final String APPENDER_NAME = "batchAdminJobLogAppender";

    private final BatchAdminLogStore store;
    private final BatchAdminProperties properties;
    private LogbackJobLogAppender appender;

    public BatchAdminLogbackInitializer(BatchAdminLogStore store, BatchAdminProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .info("[batch-admin] Logback not detected; per-execution log capture is disabled.");
            return;
        }
        appender = new LogbackJobLogAppender(store, properties.getLogs().getCaptureLevel());
        appender.setName(APPENDER_NAME);
        appender.setContext(context);
        appender.start();
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
    }

    @Override
    public void destroy() {
        if (appender == null) {
            return;
        }
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext context) {
            context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        }
        appender.stop();
    }
}
