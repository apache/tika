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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * A request thread can race a teardown into {@code ensureRunning}. Without a closed latch it
 * forks a replacement that nothing owns and nothing will ever destroy; the child only exits when
 * the parent JVM does. PerClientServerManager has guarded this since 4.0.0; shared mode did not.
 */
public class SharedServerManagerClosedTest {

    @Test
    public void testEnsureRunningAfterShutdownDoesNotFork() throws Exception {
        SharedServerManager sm = new SharedServerManager(new PipesConfig(), null, 2);
        sm.shutdown();
        assertThrows(IllegalStateException.class, sm::ensureRunning,
                "ensureRunning must refuse to fork once the manager has been shut down");
    }

    @Test
    public void testShutdownIsIdempotent() throws Exception {
        SharedServerManager sm = new SharedServerManager(new PipesConfig(), null, 2);
        sm.shutdown();
        assertDoesNotThrow(sm::shutdown, "a second shutdown must be a no-op, not a failure");
    }
}
