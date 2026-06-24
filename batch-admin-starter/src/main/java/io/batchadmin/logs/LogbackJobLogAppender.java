package io.batchadmin.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import java.time.Instant;

/**
 * Logback appender that captures, into the {@link BatchAdminLogStore}, the log events emitted while
 * a job runs (identified by the {@link JobLogExecutionListener#MDC_EXECUTION_ID} MDC value), keeping
 * only those at or above the configured capture level.
 */
public class LogbackJobLogAppender extends AppenderBase<ILoggingEvent> {

    private final BatchAdminLogStore store;
    private final int captureRank;

    public LogbackJobLogAppender(BatchAdminLogStore store, String captureLevel) {
        this.store = store;
        this.captureRank = LogLevels.rank(captureLevel);
    }

    @Override
    protected void append(ILoggingEvent event) {
        String executionId = event.getMDCPropertyMap().get(JobLogExecutionListener.MDC_EXECUTION_ID);
        if (executionId == null) {
            return; // not emitted during a tracked job execution
        }
        if (LogLevels.rank(event.getLevel().toString()) < captureRank) {
            return;
        }
        long id;
        try {
            id = Long.parseLong(executionId);
        } catch (NumberFormatException ex) {
            return;
        }
        store.append(id, new JobLogEntry(
                Instant.ofEpochMilli(event.getTimeStamp()),
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getThreadName(),
                buildMessage(event)));
    }

    private String buildMessage(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            return message + System.lineSeparator() + ThrowableProxyUtil.asString(throwable);
        }
        return message;
    }
}
