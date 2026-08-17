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
package org.apache.tika.server.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.OutputLimits;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.server.core.ServerStatus;

/**
 * A request carries only what the request itself specifies; config-level
 * {@code parse-context} defaults stay on the server and are re-supplied by the forked worker
 * from the same config.
 * <p>
 * This is a correctness boundary, not just wire economy: the worker clamps request-supplied
 * timeout limits and trusts its own config's, so a config default that travels as request data
 * is silently downgraded to caller input. That the clamp then leaves such a request alone is
 * pinned by ServerProtocolIOTest#testServerConfigLimitsAreTrustedAndNeverClamped; together the
 * two cover the path end to end.
 */
public class RequestContextIsolationTest {

    private static final String CONFIG = """
            {
              "parse-context": {
                "timeout-limits": {"totalTaskTimeoutMillis": 7200000},
                "output-limits": {"writeLimit": 12345, "throwOnWriteLimit": false},
                "standard-metadata-limiter-factory": {"excludeFields": ["dropped-field"]}
              }
            }
            """;

    @TempDir
    Path tmp;

    private TikaResource newTikaResource(String configJson) throws Exception {
        Path configPath = tmp.resolve("tika-config-" + configJson.hashCode() + ".json");
        Files.writeString(configPath, configJson);
        return new TikaResource(TikaLoader.load(configPath), new ServerStatus(), null, true);
    }

    private TikaLoader newLoader(String configJson) throws Exception {
        Path configPath = tmp.resolve("loader-config-" + configJson.hashCode() + ".json");
        Files.writeString(configPath, configJson);
        return TikaLoader.load(configPath);
    }

    /**
     * The core invariant. Asserts against the loader first so the test cannot pass vacuously
     * on a config whose defaults never resolved.
     */
    @Test
    public void configDefaultsDoNotTravelInTheRequestContext() throws Exception {
        ParseContext configDefaults = newLoader(CONFIG).loadParseContext();
        assertNotNull(configDefaults.get(TimeoutLimits.class),
                "precondition: config should declare timeout-limits");
        assertNotNull(configDefaults.get(OutputLimits.class),
                "precondition: config should declare output-limits");

        ParseContext request = newTikaResource(CONFIG).createRequestContext();

        assertNull(request.get(TimeoutLimits.class),
                "config timeout limits must not reach the worker as request data -- it clamps "
                        + "request-supplied limits but trusts its own config's");
        assertNull(request.get(OutputLimits.class));
        assertTrue(request.getContextMap().isEmpty(),
                "request context should carry only this request's own entries");
    }

    /**
     * OutputLimits.get() falls back to defaults when absent, so sourcing the handler factory's
     * limits from the now-empty request context would silently swap the operator's write limit
     * for the default -- and this factory is the one the worker honors.
     */
    @Test
    public void configuredWriteLimitStillReachesTheContentHandlerFactory() throws Exception {
        TikaResource tikaResource = newTikaResource(CONFIG);
        ParseContext request = tikaResource.createRequestContext();

        tikaResource.setupContentHandlerFactory(request, "text");

        BasicContentHandlerFactory chf =
                (BasicContentHandlerFactory) request.get(ContentHandlerFactory.class);
        assertEquals(12345, chf.getWriteLimit(), "configured writeLimit must survive");
        assertEquals(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, chf.getType());
    }

    /** The write limiter no longer rides in the context, so it must be applied to the metadata. */
    @Test
    public void configuredMetadataLimiterStillBoundsRequestMetadata() throws Exception {
        Metadata metadata = newTikaResource(CONFIG).newRequestMetadata();

        metadata.set("dropped-field", "value");
        metadata.set("kept-field", "value");

        assertNull(metadata.get("dropped-field"),
                "excluded field should be dropped by the configured write limiter");
        assertEquals("value", metadata.get("kept-field"));
    }

    /** No configured limiter: plain metadata, no NPE on the null-factory path. */
    @Test
    public void requestMetadataWorksWithoutAConfiguredLimiter() throws Exception {
        Metadata metadata = newTikaResource("{}").newRequestMetadata();
        metadata.set("kept-field", "value");
        assertEquals("value", metadata.get("kept-field"));
    }

    /**
     * A config-declared handler factory still wins over the endpoint default. It is no longer
     * visible in the request context, so precedence is preserved by leaving the context empty
     * and letting the worker resolve the same factory from the same config.
     */
    @Test
    public void configContentHandlerFactoryStillWinsOverEndpointDefault() throws Exception {
        String withHandler = """
                {
                  "parse-context": {
                    "basic-content-handler-factory": {"type": "HTML"}
                  }
                }
                """;
        TikaResource tikaResource = newTikaResource(withHandler);
        ParseContext request = tikaResource.createRequestContext();

        tikaResource.setupContentHandlerFactoryIfNeeded(request, "text");

        assertNull(request.get(ContentHandlerFactory.class),
                "endpoint default must not override the config-declared factory");
    }

    /**
     * The unpack path mutates UnpackConfig and so overrides the worker's own, which makes it the
     * one config default that must still travel. Starting from a default-constructed instance
     * would silently reset operator settings the request never touches -- here the
     * {@code maxUnpackBytes} cap.
     */
    @Test
    public void configUnpackConfigIsHandedOutPerRequestAndKeepsOperatorValues() throws Exception {
        String withUnpack = """
                {
                  "parse-context": {
                    "unpack-config": {"maxUnpackBytes": 4242, "zeroPadName": 7}
                  }
                }
                """;
        TikaResource tikaResource = newTikaResource(withUnpack);

        UnpackConfig first = tikaResource.newConfigUnpackConfig();
        assertNotNull(first, "config-declared unpack-config should be available to the request");
        assertEquals(4242, first.getMaxUnpackBytes());
        assertEquals(7, first.getZeroPadName());

        // The unpack path mutates what it is given, so each request needs its own instance.
        first.setZipEmbeddedFiles(true);
        UnpackConfig second = tikaResource.newConfigUnpackConfig();
        assertNotSame(first, second, "each request must get its own mutable copy");
        assertFalse(second.isZipEmbeddedFiles(),
                "one request's mutation must not leak into the next");
        assertEquals(4242, second.getMaxUnpackBytes());
    }

    /** No config-declared unpack-config: null, and the unpack path falls back as before. */
    @Test
    public void noConfigUnpackConfigYieldsNull() throws Exception {
        assertNull(newTikaResource("{}").newConfigUnpackConfig());
    }

    /** Without a config-declared factory, the endpoint's handler type is installed as before. */
    @Test
    public void endpointHandlerTypeIsInstalledWhenConfigDeclaresNone() throws Exception {
        TikaResource tikaResource = newTikaResource("{}");
        ParseContext request = tikaResource.createRequestContext();

        tikaResource.setupContentHandlerFactoryIfNeeded(request, "text");

        BasicContentHandlerFactory chf =
                (BasicContentHandlerFactory) request.get(ContentHandlerFactory.class);
        assertNotNull(chf);
        assertEquals(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, chf.getType());
    }
}
