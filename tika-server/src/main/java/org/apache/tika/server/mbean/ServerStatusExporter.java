package org.apache.tika.server.mbean;

import org.apache.tika.server.ServerStatus;

/**
 * Server status JMX MBean exporter.
 * Abstracts the ServerStatus, allowing only getters on server status.
 * Used for monitoring only.
 */
public class ServerStatusExporter implements ServerStatusExporterMBean {

    /**
     * The server status object currently in use by the server.
     */
    private ServerStatus serverStatus;

    /**
     * Initiates exporter with server status.
     *
     * @param serverStatus the server status object currently in use by the server.
     */
    public ServerStatusExporter(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getServerId() {
        return serverStatus.getServerId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStatus() {
        return serverStatus.getStatus().name();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMillisSinceLastParseStarted() {
        return serverStatus.getMillisSinceLastParseStarted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFilesProcessed() {
        return serverStatus.getFilesProcessed();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumRestarts() {
        return serverStatus.getNumRestarts();
    }

}
