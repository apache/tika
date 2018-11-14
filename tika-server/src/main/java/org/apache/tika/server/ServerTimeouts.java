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
package org.apache.tika.server;

public class ServerTimeouts {

    /*
    TODO: integrate these settings:
     * Number of milliseconds to wait to start child process.
    public static final long DEFAULT_CHILD_PROCESS_STARTUP_MILLIS = 60000;

     * Maximum number of milliseconds to wait to shutdown child process to allow
     * for current parses to complete.
    public static final long DEFAULT_CHILD_PROCESS_SHUTDOWN_MILLIS = 30000;

    private long childProcessStartupMillis = DEFAULT_CHILD_PROCESS_STARTUP_MILLIS;

    private long childProcessShutdownMillis = DEFAULT_CHILD_PROCESS_SHUTDOWN_MILLIS;

     */



    /**
     * If the child doesn't receive a ping or the parent doesn't
     * hear back from a ping in this amount of time, kill and restart the child.
     */
    public static final long DEFAULT_PING_TIMEOUT_MILLIS = 30000;

    /**
     * How often should the parent try to ping the child to check status
     */
    public static final long DEFAULT_PING_PULSE_MILLIS = 500;

    /**
     * Number of milliseconds to wait per server task (parse, detect, unpack, translate,
     * etc.) before timing out and shutting down the child process.
     */
    public static final long DEFAULT_TASK_TIMEOUT_MILLIS = 120000;

    private int maxRestarts = -1;

    private long taskTimeoutMillis = DEFAULT_TASK_TIMEOUT_MILLIS;

    private long pingTimeoutMillis = DEFAULT_PING_TIMEOUT_MILLIS;

    private long pingPulseMillis = DEFAULT_PING_PULSE_MILLIS;


    /**
     * How long to wait for a task before shutting down the child server process
     * and restarting it.
     * @return
     */
    public long getTaskTimeoutMillis() {
        return taskTimeoutMillis;
    }

    /**
     *
     * @param taskTimeoutMillis number of milliseconds to allow per task
     *                          (parse, detection, unzipping, etc.)
     */
    public void setTaskTimeoutMillis(long taskTimeoutMillis) {
        this.taskTimeoutMillis = taskTimeoutMillis;
    }

    public long getPingTimeoutMillis() {
        return pingTimeoutMillis;
    }

    /**
     *
     * @param pingTimeoutMillis if the parent doesn't receive a response
     *                          in this amount of time, or
     *                          if the child doesn't receive a ping
     *                          in this amount of time, restart the child process
     */
    public void setPingTimeoutMillis(long pingTimeoutMillis) {
        this.pingTimeoutMillis = pingTimeoutMillis;
    }

    public long getPingPulseMillis() {
        return pingPulseMillis;
    }

    /**
     *
     * @param pingPulseMillis how often to test that the parent and/or child is alive
     */
    public void setPingPulseMillis(long pingPulseMillis) {
        this.pingPulseMillis = pingPulseMillis;
    }

    public int getMaxRestarts() {
        return maxRestarts;
    }

    public void setMaxRestarts(int maxRestarts) {
        this.maxRestarts = maxRestarts;
    }
}
