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
package org.apache.tika.pipes.core;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeoutException;

/**
 * Manages the lifecycle of a PipesServer process and client connections.
 * <p>
 * Implementations handle starting, monitoring, and restarting the server process.
 * In per-client mode (default), each PipesClient has its own ServerManager.
 * In shared mode, multiple PipesClients share a single ServerManager.
 *
 * @see PerClientServerManager
 * @see SharedServerManager
 */
public interface ServerManager extends Closeable {

    /**
     * Returns the port number the server is listening on.
     * May return -1 if the server hasn't been started yet.
     *
     * @return the server port, or -1 if not started
     */
    int getPort();

    /**
     * Ensures the server is running, starting or restarting it if necessary.
     * <p>
     * In shared mode, this method is synchronized to prevent multiple clients
     * from attempting to restart the server simultaneously.
     *
     * @throws IOException if the server cannot be started
     * @throws InterruptedException if interrupted while waiting for server startup
     * @throws TimeoutException if the server doesn't start within the configured timeout
     * @throws ServerInitializationException if the server fails to initialize
     */
    void ensureRunning() throws IOException, InterruptedException, TimeoutException, ServerInitializationException;

    /**
     * Establishes a connection to the server and returns a connected Socket.
     * <p>
     * The behavior differs by implementation:
     * <ul>
     *   <li>Per-client mode: Accepts incoming connection from the dedicated server</li>
     *   <li>Shared mode: Connects out to the shared server</li>
     * </ul>
     * <p>
     * This method should be called after {@link #ensureRunning()}.
     *
     * @param socketTimeoutMillis the socket timeout in milliseconds
     * @return a connected Socket ready for communication
     * @throws IOException if connection fails
     * @throws ServerInitializationException if the server died before connecting
     */
    Socket connect(int socketTimeoutMillis) throws IOException, ServerInitializationException;

    /**
     * Shuts down the server process and cleans up resources.
     * After calling this method, {@link #ensureRunning()} can be called to restart.
     *
     * @throws InterruptedException if interrupted while waiting for shutdown
     */
    void shutdown() throws InterruptedException;

    /**
     * Checks if the server process is currently running.
     *
     * @return true if the server process is running
     */
    boolean isRunning();

    /**
     * Returns the path to the temporary directory used by the server.
     * May return null if the server hasn't been started yet.
     *
     * @return the temp directory path, or null if not started
     */
    java.nio.file.Path getTempDirectory();

    /**
     * The generation of the currently running process: a counter incremented every time this
     * manager forks a replacement. A client captures it when it connects and hands it back with
     * every report, so a report about a process that has already been replaced can be recognised
     * and dropped rather than being applied to its healthy successor.
     */
    long getGeneration();

    /**
     * Marks the server for restart due to a fatal error, attributed to {@code reason}, but only
     * if {@code generation} is still current -- reports about a superseded process are dropped.
     * <p>
     * Called by a client that received a fatal status: the process is stopping even if
     * {@link #isRunning()} still says otherwise, and the next {@link #ensureRunning()} waits for
     * it to exit and restarts it.
     * <p>
     * Deliberately the only spelling, and deliberately abstract. Earlier revisions offered a
     * no-arg and a reasonless form defaulting to one another; an implementation that overrode
     * only one left the others silently inert, which is how a worker known to be poisoned kept
     * being handed documents.
     */
    void markServerForRestart(RestartReason reason, long generation);

    /** Restarts performed so far for {@code reason}; monotonic, never reset. */
    default long getRestartCount(RestartReason reason) {
        return 0;
    }

    /**
     * Signals that the client walked away from its connection, whether
     * mid-handshake or with a request still in flight.
     * <p>
     * A per-client worker dials the parent once and never dials back, so an
     * abandoned worker must be recycled or the next {@link #connect(int)} waits
     * out the full accept timeout against a process that will never call. A
     * shared server outlives its clients and needs nothing here; its
     * connection handlers already detect the dead socket themselves.
     */
    default void connectionAbandoned() {
        // Default no-op: only per-client mode must recycle the worker
    }

    /**
     * Increments the count of files processed and marks for restart if limit reached.
     * <p>
     * This tracks progress toward the maxFilesProcessedPerProcess limit. When the limit
     * is reached, {@link #needsRestart()} will return true and the next call to
     * {@link #ensureRunning()} will restart the server.
     *
     * @param maxFilesPerProcess the maximum files before restart (0 means unlimited)
     */
    default void incrementFilesProcessed(long maxFilesPerProcess) {
        // Default no-op for backward compatibility
    }

    /**
     * Checks if the server has been marked for restart.
     * <p>
     * This allows clients to detect that a restart is pending before attempting
     * to use an existing connection that might be stale.
     *
     * @return true if the server has been marked for restart
     */
    default boolean needsRestart() {
        return false;
    }

    /**
     * Handles a crash by checking the process exit code and marking for restart.
     * <p>
     * In per-client mode, waits briefly for the process to exit and checks the
     * exit code to determine if this was an OOM or TIMEOUT.
     * In shared mode, just marks for restart (exit code checking is not reliable
     * since multiple clients share the process).
     *
     * @return the exit code if available, or -1 if the process is still running or unavailable
     */
    int handleCrashAndGetExitCode(long generation);

}
