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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.plugins.TikaPluginManager;

public class PipesParser implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(PipesParser.class);

    /**
     * Loads a PipesParser from a configuration file path.
     * <p>
     * This method:
     * <ol>
     *   <li>Loads the JSON configuration</li>
     *   <li>Pre-extracts plugins before spawning child processes</li>
     *   <li>Creates the PipesParser with the loaded configuration</li>
     * </ol>
     *
     * @param tikaConfigPath path to the tika-config.json file
     * @return a new PipesParser instance
     * @throws IOException if reading config or extraction fails
     * @throws TikaConfigException if configuration is invalid
     */
    public static PipesParser load(Path tikaConfigPath) throws IOException, TikaConfigException {
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        return load(tikaJsonConfig, pipesConfig, tikaConfigPath);
    }

    /**
     * Loads a PipesParser from pre-loaded configuration objects.
     * <p>
     * Use this method when you need to modify the PipesConfig before creating
     * the parser (e.g., to override emit strategy).
     *
     * @param tikaJsonConfig the pre-loaded JSON configuration
     * @param pipesConfig the pipes configuration (may be modified by caller)
     * @param tikaConfigPath path to the config file (passed to child processes)
     * @return a new PipesParser instance
     * @throws IOException if plugin extraction fails
     */
    public static PipesParser load(TikaJsonConfig tikaJsonConfig, PipesConfig pipesConfig,
            Path tikaConfigPath) throws IOException {
        TikaPluginManager.preExtractPlugins(tikaJsonConfig);
        return new PipesParser(pipesConfig, tikaConfigPath);
    }

    private final PipesConfig pipesConfig;
    private final Path tikaConfigPath;
    private final List<PipesClient> clients = new ArrayList<>();
    private final List<ServerManager> serverManagers = new ArrayList<>();
    private final ArrayBlockingQueue<PipesClient> clientQueue;
    private final boolean isSharedMode;

    private PipesParser(PipesConfig pipesConfig, Path tikaConfigPath) {
        this.pipesConfig = pipesConfig;
        this.tikaConfigPath = tikaConfigPath;
        this.isSharedMode = pipesConfig.isUseSharedServer();
        this.clientQueue = new ArrayBlockingQueue<>(pipesConfig.getNumClients());

        if (isSharedMode) {
            // Shared mode: one ServerManager for all clients
            LOG.info("Using shared server mode with {} clients", pipesConfig.getNumClients());
            SharedServerManager sharedManager = new SharedServerManager(
                    pipesConfig, tikaConfigPath, pipesConfig.getNumClients());
            serverManagers.add(sharedManager);

            for (int i = 0; i < pipesConfig.getNumClients(); i++) {
                PipesClient client = new PipesClient(pipesConfig, sharedManager);
                clientQueue.offer(client);
                clients.add(client);
            }
        } else {
            // Per-client mode: each client has its own ServerManager
            LOG.info("Using per-client server mode with {} clients", pipesConfig.getNumClients());
            for (int i = 0; i < pipesConfig.getNumClients(); i++) {
                PerClientServerManager serverManager = new PerClientServerManager(
                        pipesConfig, tikaConfigPath, i);
                serverManagers.add(serverManager);

                PipesClient client = new PipesClient(pipesConfig, serverManager);
                clientQueue.offer(client);
                clients.add(client);
            }
        }
    }

    public PipesResult parse(FetchEmitTuple t) throws InterruptedException,
            PipesException, IOException {
        PipesClient client = null;
        try {
            client = clientQueue.poll(pipesConfig.getMaxWaitForClientMillis(),
                    TimeUnit.MILLISECONDS);
            if (client == null) {
                return PipesResults.CLIENT_UNAVAILABLE_WITHIN_MS;
            }
            return client.process(t);
        } finally {
            if (client != null) {
                clientQueue.offer(client);
            }
        }
    }

    @Override
    public void close() throws IOException {
        List<IOException> exceptions = new ArrayList<>();

        // First close all clients (closes their connections)
        for (PipesClient pipesClient : clients) {
            try {
                pipesClient.close();
            } catch (IOException e) {
                exceptions.add(e);
            }
        }

        // Then close all server managers (shuts down server processes)
        for (ServerManager serverManager : serverManagers) {
            try {
                serverManager.close();
            } catch (IOException e) {
                exceptions.add(e);
            }
        }

        if (!exceptions.isEmpty()) {
            throw exceptions.get(0);
        }
    }

    /**
     * Returns whether this parser is using shared server mode.
     *
     * @return true if using shared server mode
     */
    public boolean isSharedMode() {
        return isSharedMode;
    }

    /**
     * Returns the current server port. For testing purposes only.
     * In shared mode, returns the port of the shared server.
     * In per-client mode, returns the port of the first client's server.
     *
     * @return the current server port, or -1 if no server is running
     */
    public int getCurrentServerPort() {
        if (serverManagers.isEmpty()) {
            return -1;
        }
        return serverManagers.get(0).getPort();
    }
}
