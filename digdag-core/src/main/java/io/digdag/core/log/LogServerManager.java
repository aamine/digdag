package io.digdag.core.log;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import com.google.inject.Inject;
import io.digdag.spi.LogServer;
import io.digdag.spi.LogServerFactory;
import io.digdag.spi.LogFilePrefix;
import io.digdag.spi.Storage;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigFactory;
import io.digdag.client.config.ConfigException;
import io.digdag.core.agent.AgentId;
import io.digdag.core.storage.StorageManager;
import io.digdag.core.log.NullLogServerFactory.NullLogServer;
import io.digdag.core.log.LocalFileLogServerFactory.LocalFileLogServer;
import io.digdag.core.TempFileManager;

public class LogServerManager
{
    private static final DateTimeFormatter ISO8601_SHORT = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmssX", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private final LogServer logServer;
    private final TempFileManager tempFiles;

    @Inject
    public LogServerManager(Set<LogServerFactory> factories, Config systemConfig, TempFileManager tempFiles,
            StorageManager storageManager)
    {
        String type = systemConfig.get("log-server.type", String.class, "null");
        LogServerFactory factory = findLogServer(factories, type);
        if (factory == null) {
            this.logServer = newStorageLogServer(storageManager, type, systemConfig);
        }
        else {
            this.logServer = factory.getLogServer();
        }
        this.tempFiles = tempFiles;
    }

    private static LogServerFactory findLogServer(Set<LogServerFactory> factories, String type)
    {
        for (LogServerFactory factory : factories) {
            if (type.equals(factory.getType())) {
                return factory;
            }
        }
        return null;
    }

    public LogServer newStorageLogServer(StorageManager storageManager,
            String type, Config systemConfig)
    {
        Storage storage = storageManager.create(type, systemConfig, "log-server.");

        String logPath = systemConfig.get("log-server." + type + ".path", String.class, "");
        boolean directDownload = systemConfig.get("log-server." + type + ".direct_download", boolean.class, false);
        if (logPath.startsWith("/")) {
            logPath = logPath.substring(1);
        }
        if (!logPath.endsWith("/") && !logPath.isEmpty()) {
            logPath = logPath + "/";
        }

        return new StorageFileLogServer(storage, logPath, directDownload);
    }

    public LogServer getLogServer()
    {
        return logServer;
    }

    // this is called when server == agent (server runs a local agent).
    public TaskLogger newInProcessTaskLogger(AgentId agentId, LogFilePrefix prefix, String taskName)
    {
        if (logServer instanceof NullLogServer) {
            return new NullTaskLogger();
        }
        else if (logServer instanceof LocalFileLogServer) {
            return ((LocalFileLogServer) logServer).newDirectTaskLogger(prefix, taskName);
        }
        else {
            // temp file prefix: {projectId}_{workflowName}_{sessionTime}
            final String tempFilePrefix = new StringBuilder()
                    .append(prefix.getProjectId()).append("_")
                    .append(prefix.getWorkflowName()).append("_") // workflow name is normalized before it's submitted.
                    .append(ISO8601_SHORT.format(prefix.getSessionTime())) // yyyyMMdd'T'HHmmss'Z'
                    .toString();
            return new BufferedRemoteTaskLogger(tempFiles, tempFilePrefix,
                    (firstLogTime, gzData) -> {
                        logServer.putFile(prefix, taskName, firstLogTime, agentId.toString(), gzData);
                    });
        }
    }
}
