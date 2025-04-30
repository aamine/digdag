package io.digdag.core.agent;

import io.digdag.spi.TaskExecutionException;

import static com.google.common.base.Strings.isNullOrEmpty;

public class OperatorManager
{
    public static String formatExceptionMessage(Throwable ex)
    {
        StringBuilder sb = new StringBuilder();
        collectExceptionMessage(sb, ex, new StringBuffer());
        return sb.toString();
    }

    public static void collectExceptionMessage(StringBuilder sb, Throwable ex, StringBuffer used)
    {
        String message = ex.getMessage();
        if (isNullOrEmpty(message)) {
            message = ex.getClass().getSimpleName();
        }
        if (used.indexOf(message) == -1) {
            used.append("\n").append(message);
            if (sb.length() > 0) {
                sb.append("\n> ");
            }
            sb.append(message);
            if (!(ex instanceof TaskExecutionException)) {
                // skip TaskExecutionException because it's expected to have well-formatted message
                sb.append(" (");
                sb.append(ex.getClass().getSimpleName()
                            .replaceFirst("(?:Exception|Error)$", "")
                            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                            .replaceAll("([a-z])([A-Z])", "$1 $2")
                            .toLowerCase());
                sb.append(")");
            }
        }
        if (ex.getCause() != null) {
            collectExceptionMessage(sb, ex.getCause(), used);
        }
        for (Throwable t : ex.getSuppressed()) {
            collectExceptionMessage(sb, t, used);
        }
    }
}
