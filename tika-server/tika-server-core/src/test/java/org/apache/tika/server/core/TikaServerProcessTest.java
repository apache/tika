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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;

public class TikaServerProcessTest {

    private static TikaServerConfig config(boolean unsecure, String... endpoints) {
        TikaServerConfig c = new TikaServerConfig();
        c.setEndpoints(new ArrayList<>(List.of(endpoints)));
        c.setEnableUnsecureFeatures(unsecure);
        return c;
    }

    @Test
    public void pipesAndAsyncRequireUnsecureFeatures() {
        // The pipes/async endpoints fork processes and read/write via fetchers/emitters; the
        // start-guard must refuse them unless enableUnsecureFeatures is set, even when listed.
        assertThrows(TikaConfigException.class,
                () -> TikaServerProcess.loadCoreProviders(config(false, "pipes"), null));
        assertThrows(TikaConfigException.class,
                () -> TikaServerProcess.loadCoreProviders(config(false, "async"), null));
    }

    @Test
    public void ordinaryEndpointIsAllowedWithoutUnsecureFeatures() {
        // The guard must not false-fire on a non-forking endpoint.
        assertDoesNotThrow(
                () -> TikaServerProcess.loadCoreProviders(config(false, "meta"), null));
    }
}
