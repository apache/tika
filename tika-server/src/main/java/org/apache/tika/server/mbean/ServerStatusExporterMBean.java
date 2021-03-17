package org.apache.tika.server.mbean;

public interface ServerStatusExporterMBean {

    String getServerId();

    String getStatus();

    long getMillisSinceLastParseStarted();

    long getFilesProcessed();

    int getNumRestarts();

}
