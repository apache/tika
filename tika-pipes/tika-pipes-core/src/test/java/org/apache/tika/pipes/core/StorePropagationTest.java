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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.FetcherNotFoundException;
import org.apache.tika.pipes.core.TrackingFetcherManager.TrackedFetcher;
import org.apache.tika.pipes.core.config.FileBasedConfigStore;
import org.apache.tika.pipes.core.config.InMemoryConfigStore;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * A save or delete in one process reaches another process's manager through a shared file
 * store: the gRPC server and a pipes fork, each with its own store instance on one file.
 */
public class StorePropagationTest {

    @TempDir
    Path tmp;

    private TrackingFetcherManager manager(String... configuredIds) throws Exception {
        FileBasedConfigStore store = new FileBasedConfigStore(tmp.resolve("store.json"));
        store.init();
        return new TrackingFetcherManager(store, configuredIds);
    }

    @Test
    public void testUpdateAndDeleteReachAnotherProcess() throws Exception {
        TrackingFetcherManager parent = manager();
        TrackingFetcherManager fork = manager();
        parent.saveComponent(TrackingFetcherManager.config("f"));

        Fetcher first;
        try (Lease<Fetcher> lease = fork.acquire("f")) {
            first = lease.get();
        }
        try (Lease<Fetcher> lease = fork.acquire("f")) {
            assertSame(first, lease.get(), "no change, no rebuild");
        }

        // same length as the first config: the token must not depend on size
        parent.saveComponent(new ExtensionConfig("f", TrackingFetcherManager.TYPE, "{ }"));
        try (Lease<Fetcher> lease = fork.acquire("f")) {
            assertNotSame(first, lease.get());
            assertEquals("{ }", lease.get().getExtensionConfig().json());
        }
        assertEquals(1, ((TrackedFetcher) first).getCloses());

        parent.deleteComponent("f");
        assertThrows(FetcherNotFoundException.class, () -> fork.acquire("f"));
        assertEquals(1, fork.getBuilt().get(1).getCloses());
    }

    @Test
    public void testHeldInstanceSurvivesARemoteUpdate() throws Exception {
        TrackingFetcherManager parent = manager();
        TrackingFetcherManager fork = manager();
        parent.saveComponent(TrackingFetcherManager.config("f"));

        Lease<Fetcher> inFlight = fork.acquire("f");
        parent.saveComponent(new ExtensionConfig("f", TrackingFetcherManager.TYPE, "{ }"));
        try (Lease<Fetcher> next = fork.acquire("f")) {
            assertNotSame(inFlight.get(), next.get(), "the next task gets the update");
        }
        assertEquals(0, ((TrackedFetcher) inFlight.get()).getCloses(),
                "closed under the task that still holds it");
        inFlight.close();
        assertEquals(1, ((TrackedFetcher) inFlight.get()).getCloses());
    }

    @Test
    public void testConfigFileComponentNotRetired() throws Exception {
        TrackingFetcherManager parent = manager("configured");
        TrackingFetcherManager fork = manager("configured");
        Fetcher configured;
        try (Lease<Fetcher> lease = fork.acquire("configured")) {
            configured = lease.get();
        }
        parent.saveComponent(TrackingFetcherManager.config("runtime"));
        try (Lease<Fetcher> lease = fork.acquire("configured")) {
            assertSame(configured, lease.get());
        }
        assertEquals(0, ((TrackedFetcher) configured).getCloses());
    }

    @Test
    public void testUnreachableStoreFailsOnlyRuntimeLookups() throws Exception {
        boolean[] down = {false};
        InMemoryConfigStore store = new InMemoryConfigStore() {
            @Override
            public Object changeToken() {
                if (down[0]) {
                    throw new IllegalStateException("store unreachable");
                }
                return "t";
            }
        };
        TrackingFetcherManager manager = new TrackingFetcherManager(store, "configured");
        manager.saveComponent(TrackingFetcherManager.config("runtime"));
        down[0] = true;
        // checked, so FetchHandler reports a fetcher error instead of crashing the fork
        assertThrows(IOException.class, () -> manager.acquire("runtime"));
        manager.acquire("configured").close();
    }

    @Test
    public void testDeletedStoreFileEmptiesTheStore() throws Exception {
        TrackingFetcherManager parent = manager();
        TrackingFetcherManager fork = manager();
        parent.saveComponent(TrackingFetcherManager.config("f"));
        fork.acquire("f").close();

        Files.delete(tmp.resolve("store.json"));
        assertThrows(FetcherNotFoundException.class, () -> fork.acquire("f"));
        assertEquals(1, fork.getBuilt().get(0).getCloses());
    }
}
