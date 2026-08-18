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
package org.apache.tika.server.standard.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.server.core.TikaServerProcess;
import org.apache.tika.server.core.resource.TikaServerResource;
import org.apache.tika.utils.ServiceLoaderUtils;

/**
 * Every resource this module contributes has to survive the service loader, which can only call
 * a no-arg (or ServiceLoader-arg) constructor. Adding a constructor argument to one of them
 * silently drops its endpoints at startup -- the failure is a WARN in the log and a 406 to the
 * caller, with nothing in the test suite to notice, because the resource tests construct their
 * resources directly and register them by hand.
 * <p>
 * That is how {@link XMPMetadataResource} lost {@code Accept: application/rdf+xml} on
 * {@code /meta} after gaining a {@code (TikaResource)} constructor.
 */
public class ResourceServiceLoadingTest {

    private static final String SPI_RESOURCE =
            "META-INF/services/" + TikaServerResource.class.getName();

    @Test
    public void testDeclaredResourcesAreInstantiable() throws Exception {
        List<String> declared = readDeclaredResources();
        assertFalse(declared.isEmpty(), "expected this module to declare resources at " + SPI_RESOURCE);

        ServiceLoader loader = new ServiceLoader(getClass().getClassLoader());
        for (String className : declared) {
            Class<?> klass = Class.forName(className);
            // Throws if no usable constructor exists -- exactly what the server swallows.
            Object instance = ServiceLoaderUtils.newInstance(klass, loader);
            assertTrue(instance instanceof TikaServerResource, className + " must be a TikaServerResource");
        }
    }

    /** The XMP resource in particular, since it is the one that regressed. */
    @Test
    public void testXmpResourceIsDeclaredAndLoadable() throws Exception {
        assertTrue(readDeclaredResources().contains(XMPMetadataResource.class.getName()),
                "XMPMetadataResource must stay declared -- it is what serves rdf+xml from /meta");
        ServiceLoaderUtils.newInstance(XMPMetadataResource.class,
                new ServiceLoader(getClass().getClassLoader()));
    }

    /** Omitting "meta" from 'endpoints' must remove the SPI-provided /meta surface too. */
    @Test
    public void testXmpResourceHonorsEndpointsAllowlist() {
        assertFalse(TikaServerProcess.spiResourceEnabled(XMPMetadataResource.class, Set.of("tika")),
                "an SPI resource must not bind /meta when the allowlist omits it");
        assertTrue(TikaServerProcess.spiResourceEnabled(XMPMetadataResource.class, Set.of("tika", "meta")));
    }

    private List<String> readDeclaredResources() throws IOException {
        List<String> names = new ArrayList<>();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SPI_RESOURCE)) {
            if (is == null) {
                return names;
            }
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        names.add(line);
                    }
                }
            }
        }
        return names;
    }
}
