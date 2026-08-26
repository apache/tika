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
package org.apache.tika.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class TemporaryResourcesTest {

    @Test
    public void testFileDeletion() throws IOException {
        Path tempFile;
        try (TemporaryResources tempResources = new TemporaryResources()) {
            tempFile = tempResources.createTempFile();
            assertTrue(Files.exists(tempFile), "Temp file should exist while TempResources is used");
        }
        assertTrue(Files.notExists(tempFile),
                "Temp file should not exist after TempResources is closed");
    }

    /**
     * A resource whose close() throws unchecked must not leave the rest open. Resources close
     * in reverse registration order, so the ones registered BEFORE the thrower are at risk.
     */
    @Test
    public void testUncheckedThrowDoesNotAbandonRemainingResources() throws IOException {
        AtomicInteger closed = new AtomicInteger();
        Closeable counting = closed::incrementAndGet;
        IllegalStateException boom = new IllegalStateException("boom");
        TemporaryResources tmp = new TemporaryResources();
        tmp.addResource(counting);
        Path tempFile = tmp.createTempFile();
        tmp.addResource(() -> {
            throw new IOException("checked");
        });
        tmp.addResource(counting);
        tmp.addResource(() -> {
            throw boom;
        });
        tmp.addResource(counting);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, tmp::close);
        assertSame(boom, thrown, "the first failure in close order propagates");
        assertEquals(1, thrown.getSuppressed().length, "the later checked failure is suppressed");
        assertEquals(3, closed.get(), "every counting resource closed, including those after the throw");
        assertTrue(Files.notExists(tempFile), "the temp file registered first was still deleted");
    }
}
