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
