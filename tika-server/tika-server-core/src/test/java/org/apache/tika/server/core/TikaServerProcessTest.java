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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.server.core.resource.MetadataResource;

public class TikaServerProcessTest {

    private static TikaServerConfig config(boolean allowPipes, String... endpoints) {
        TikaServerConfig c = new TikaServerConfig();
        c.setEndpoints(new ArrayList<>(List.of(endpoints)));
        c.setAllowPipes(allowPipes);
        return c;
    }

    @Test
    public void pipesAndAsyncRequireAllowPipes() {
        // The pipes/async endpoints fork processes and read/write via fetchers/emitters; the
        // start-guard must refuse them unless allowPipes is set, even when listed.
        assertThrows(TikaConfigException.class,
                () -> TikaServerProcess.loadCoreProviders(config(false, "pipes"), null, null));
        assertThrows(TikaConfigException.class,
                () -> TikaServerProcess.loadCoreProviders(config(false, "async"), null, null));
    }

    @Test
    public void ordinaryEndpointIsAllowedWithoutAllowPipes() {
        // The guard must not false-fire on a non-forking endpoint.
        assertDoesNotThrow(
                () -> TikaServerProcess.loadCoreProviders(config(false, "meta"), null, null));
    }

    @Test
    public void metaAloneNeedsPipesParsingHelper() {
        // /meta is now pipes-backed too; a config listing only "meta" (no tika/rmeta/
        // unpack/pipes) must still build the shared PipesParser, or every /meta request
        // hits IllegalStateException("Pipes-based parsing is not enabled").
        assertTrue(TikaServerProcess.needsPipesParsingHelper(config(false, "meta")));
        assertFalse(TikaServerProcess.needsPipesParsingHelper(config(false, "status")));
    }

    /** Mirrors XMPMetadataResource: subclass with no class-level @Path of its own. */
    private static class InheritsMetaPath extends MetadataResource {
        InheritsMetaPath() {
            super();
        }
    }

    @jakarta.ws.rs.Path("/my-plugin")
    private static class CustomPathResource {
    }

    private static class NoPathResource {
    }

    @Test
    public void resourcePathRootWalksSuperclasses() {
        assertEquals("meta", TikaServerProcess.resourcePathRoot(MetadataResource.class));
        assertEquals("meta", TikaServerProcess.resourcePathRoot(InheritsMetaPath.class));
        assertEquals("my-plugin", TikaServerProcess.resourcePathRoot(CustomPathResource.class));
        assertNull(TikaServerProcess.resourcePathRoot(NoPathResource.class));
    }

    @Test
    public void spiResourcesHonorEndpointsAllowlist() {
        // An SPI resource on a named endpoint binds only when that endpoint is enabled...
        assertFalse(TikaServerProcess.spiResourceEnabled(InheritsMetaPath.class, Set.of("tika")));
        assertTrue(TikaServerProcess.spiResourceEnabled(InheritsMetaPath.class, Set.of("tika", "meta")));
        // ...while a custom path loads unconditionally: installing the jar is the opt-in.
        assertTrue(TikaServerProcess.spiResourceEnabled(CustomPathResource.class, Set.of("tika")));
        assertTrue(TikaServerProcess.spiResourceEnabled(NoPathResource.class, Set.of()));
    }
}
