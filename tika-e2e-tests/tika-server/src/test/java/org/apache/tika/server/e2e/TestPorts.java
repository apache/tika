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
package org.apache.tika.server.e2e;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ports for test servers, from below the ephemeral range so the server's own pipes
 * listener ({@code bind(0)}) can't take one between allocation and Jetty's bind. Same
 * scheme as tika-server-core's TestPortAllocator, which this module can't depend on.
 */
final class TestPorts {

    private static final int LOW = 20000;
    private static final int HIGH = 32000;

    private static final AtomicInteger NEXT =
            new AtomicInteger(ThreadLocalRandom.current().nextInt(HIGH - LOW));

    private TestPorts() {
    }

    static int findFreePort() {
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
