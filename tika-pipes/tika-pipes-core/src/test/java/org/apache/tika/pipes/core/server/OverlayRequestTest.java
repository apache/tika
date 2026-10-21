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
package org.apache.tika.pipes.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.core.PipesConfig;

/** {@code pipes.trustedRequests} decides whether a request may loosen the operator's limits. */
public class OverlayRequestTest {

    @Test
    public void testClampedByDefault() throws Exception {
        ParseContext merged = overlay(new PipesConfig());
        assertEquals(5, merged.get(EmbeddedLimits.class).getMaxDepth());
        assertEquals(30_000, merged.get(TimeoutLimits.class).getTotalTaskTimeoutMillis());
    }

    @Test
    public void testUnclampedForTheOperatorsOwnRequests() throws Exception {
        PipesConfig pipesConfig = new PipesConfig();
        pipesConfig.setTrustedRequests(true);
        ParseContext merged = overlay(pipesConfig);
        assertEquals(100, merged.get(EmbeddedLimits.class).getMaxDepth());
        assertEquals(7_200_000, merged.get(TimeoutLimits.class).getTotalTaskTimeoutMillis());
    }

    @Test
    public void testServersRefuseTrustedRequests() throws Exception {
        new PipesConfig().refuseTrustedRequests("tika-server");
        PipesConfig pipesConfig = new PipesConfig();
        pipesConfig.setTrustedRequests(true);
        assertThrows(TikaConfigException.class,
                () -> pipesConfig.refuseTrustedRequests("tika-server"));
    }

    private static ParseContext overlay(PipesConfig pipesConfig) throws Exception {
        ParseContext merged = new ParseContext();
        merged.set(EmbeddedLimits.class, new EmbeddedLimits(5, false, 100, false));
        merged.set(TimeoutLimits.class, new TimeoutLimits(30_000, 10_000));
        ParseContext request = new ParseContext();
        request.set(EmbeddedLimits.class, new EmbeddedLimits(100, false, 100, false));
        request.set(TimeoutLimits.class, new TimeoutLimits(7_200_000, 10_000));
        PipesServer.overlayRequest(request, merged, pipesConfig, null);
        return merged;
    }
}
