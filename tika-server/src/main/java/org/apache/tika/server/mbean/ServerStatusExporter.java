package org.apache.tika.server.mbean;

import org.apache.tika.server.ServerStatus;

public class ServerStatusExporter implements ServerStatusExporterMBean {

    private ServerStatus serverStatus;

    public ServerStatusExporter(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }

    @Override
    public String getServerId() {
        return serverStatus.getServerId();
    }

    @Override
    public String getStatus() {
        return serverStatus.getStatus().name();
    }

    @Override
    public long getMillisSinceLastParseStarted() {
        return serverStatus.getMillisSinceLastParseStarted();
    }

    @Override
    public long getFilesProcessed() {
        return serverStatus.getFilesProcessed();
    }

    @Override
    public int getNumRestarts() {
        return serverStatus.getNumRestarts();
    }

}
