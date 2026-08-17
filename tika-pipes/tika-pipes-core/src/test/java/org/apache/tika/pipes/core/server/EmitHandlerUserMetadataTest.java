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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * The EmitHandler half of the /pipes userMetadata stance (the deserializer half is pinned by
 * {@link org.apache.tika.pipes.core.serialization.UserMetadataReservedKeyStanceTest}):
 * {@code injectUserMetadata} re-applies user metadata AFTER parse via the trusted routes, so a
 * user-supplied entry -- reserved {@code tk:} keys included -- wins over the parse-produced value.
 */
public class EmitHandlerUserMetadataTest {

    private static EmitHandler emitHandler() {
        // injectUserMetadata touches no instance state; the collaborators are irrelevant
        return new EmitHandler(null, null, null, 0);
    }

    @Test
    public void userSuppliedReservedKeyOverwritesParseProducedValue() {
        Metadata parsed = new Metadata();
        parsed.set(TikaCoreProperties.TIKA_CONTENT, "parse-produced");
        parsed.add(TikaCoreProperties.TIKA_PARSED_BY, "org.apache.tika.RealParser");

        // as built by FetchEmitTupleDeserializer: reconstruct, the trusted route
        Metadata user = new Metadata();
        user.reconstruct(TikaCoreProperties.TIKA_CONTENT.getName(), "user-wins", false);
        user.reconstruct(TikaCoreProperties.TIKA_PARSED_BY.getName(), "user-asserted-1", true);
        user.reconstruct(TikaCoreProperties.TIKA_PARSED_BY.getName(), "user-asserted-2", true);

        emitHandler().injectUserMetadata(user, List.of(parsed));

        assertEquals("user-wins", parsed.get(TikaCoreProperties.TIKA_CONTENT));
        // wholesale replace, in order -- not appended after the parse-produced value
        assertArrayEquals(new String[] {"user-asserted-1", "user-asserted-2"},
                parsed.getValues(TikaCoreProperties.TIKA_PARSED_BY));
    }

    @Test
    public void nonReservedUserKeysLandAndOthersAreUntouched() {
        Metadata parsed = new Metadata();
        parsed.set("dc:title", "parsed title");
        parsed.set("keep:me", "untouched");

        Metadata user = new Metadata();
        user.set("dc:title", "user title");

        emitHandler().injectUserMetadata(user, List.of(parsed));

        assertEquals("user title", parsed.get("dc:title"));
        assertEquals("untouched", parsed.get("keep:me"));
    }
}
