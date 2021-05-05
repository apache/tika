/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
