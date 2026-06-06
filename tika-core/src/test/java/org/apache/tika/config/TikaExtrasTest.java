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
package org.apache.tika.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TikaExtrasTest {

    private static final String MARKER = "tika-extra-marker.txt";

    @Test
    public void offWhenPropertyUnset() throws Exception {
        withProperty(null, () -> {
            assertNull(TikaExtras.extrasDir(), "feature must be off with no property");
            assertTrue(TikaExtras.extraJars().isEmpty());
            assertNull(TikaExtras.install());
        });
    }

    @Test
    public void emptyWhenDirMissing(@TempDir Path tmp) throws Exception {
        Path missing = tmp.resolve("does-not-exist");
        withProperty(missing.toString(), () -> {
            assertTrue(TikaExtras.extraJars().isEmpty());
            assertNull(TikaExtras.install());
        });
    }

    @Test
    public void loadsJarsWhenPropertySet(@TempDir Path tmp) throws Exception {
        Path jar = tmp.resolve("extra.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            jos.putNextEntry(new JarEntry(MARKER));
            jos.write("hi".getBytes(UTF_8));
            jos.closeEntry();
        }
        ClassLoader prevCtx = Thread.currentThread().getContextClassLoader();
        try {
            withProperty(tmp.toString(), () -> {
                assertEquals(1, TikaExtras.extraJars().size());
                ClassLoader cl = TikaExtras.install();
                assertNotNull(cl, "extras dir with a jar should install a classloader");
                assertNotNull(cl.getResource(MARKER), "the extra jar must be on the classloader");
                assertSame(cl, Thread.currentThread().getContextClassLoader());
            });
        } finally {
            Thread.currentThread().setContextClassLoader(prevCtx);
            ServiceLoader.setContextClassLoader(prevCtx);
        }
    }

    private interface Body {
        void run() throws Exception;
    }

    private static void withProperty(String value, Body body) throws Exception {
        String prev = System.getProperty(TikaExtras.EXTRAS_DIR_PROPERTY);
        try {
            if (value == null) {
                System.clearProperty(TikaExtras.EXTRAS_DIR_PROPERTY);
            } else {
                System.setProperty(TikaExtras.EXTRAS_DIR_PROPERTY, value);
            }
            body.run();
        } finally {
            if (prev == null) {
                System.clearProperty(TikaExtras.EXTRAS_DIR_PROPERTY);
            } else {
                System.setProperty(TikaExtras.EXTRAS_DIR_PROPERTY, prev);
            }
        }
    }
}
