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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Allocates ports for test servers.
 * <p>
 * Ports come from below the ephemeral range (Linux 32768-60999, Windows/macOS
 * 49152-65535), which the OS never hands out for {@code bind(0)} or outgoing
 * connections. An ephemeral "free" port, once released, can be taken by the server's
 * own pipes listener before Jetty binds it. Each JVM starts at a random offset so
 * parallel test JVMs rarely meet; a port is checked free before it is returned.
 */
public final class TestPortAllocator {

    static final int LOW = 20000;
    static final int HIGH = 32000;

    private static final AtomicInteger NEXT =
            new AtomicInteger(ThreadLocalRandom.current().nextInt(HIGH - LOW));

    private TestPortAllocator() {
    }

    public static int findFreePort() {
        for (int i = 0; i < HIGH - LOW; i++) {
            int port = LOW + Math.floorMod(NEXT.getAndIncrement(), HIGH - LOW);
            try (ServerSocket socket = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                // in use; try the next
            }
        }
        throw new IllegalStateException("No free port in " + LOW + "-" + HIGH);
    }
}
