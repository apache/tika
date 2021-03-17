package org.apache.tika.server.mbean;

/**
 * Server status JMX MBean exporter interface.
 * Abstracts the ServerStatus.
 */
public interface ServerStatusExporterMBean {

    /**
     * Gets server id.
     *
     * @return the server id.
     */
    String getServerId();

    /**
     * Gets the current operating status as string.
     *
     * @return the operating status.
     */
    String getStatus();

    /**
     * Gets the milliseconds passed since last parse started.
     *
     * @return the milliseconds passed since last parse started
     */
    long getMillisSinceLastParseStarted();

    /**
     * Gets the number of files processed in this cycle.
     *
     * @return the number of files processed in this cycle.
     */
    long getFilesProcessed();

    /**
     * Gets the number of child restart.
     *
     * @return the number of child restart.
     */
    int getNumRestarts();

}
