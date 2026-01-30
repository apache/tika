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
package org.apache.tika.server.core;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Allocates ephemeral ports for tests to avoid conflicts when running
 * multiple builds simultaneously.
 */
public final class TestPortAllocator {

    private TestPortAllocator() {
    }

    /**
     * Finds and returns an available port.
     * <p>
     * Note: There is a small race window between closing the ServerSocket
     * and the test actually binding to the port. In practice, this is
     * rarely an issue since ephemeral ports are drawn from a large pool.
     */
    public static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Could not find free port", e);
        }
    }
}
