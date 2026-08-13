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
package org.apache.tika.pipes.core.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.api.FetchEmitTuple;

/**
 * Pins the accepted stance: {@code FetchEmitTupleDeserializer} builds userMetadata via
 * {@link Metadata#reconstruct}, a deliberately trusted route, so a requester can assert
 * reserved {@code tk:} keys in their own request. Accepted, not a bug -- deployment-level
 * authn/authz on who may submit tuples is the actual trust boundary, not this deserializer.
 */
public class UserMetadataReservedKeyStanceTest {

    @Test
    public void userMetadataCanAssertReservedKeys_acceptedStance() throws IOException {
        String json = """
                {
                  "id": "id1",
                  "fetcher": "fs",
                  "fetchKey": "fetchKey1",
                  "metadata": {
                    "tk:content": "attacker-injected",
                    "tk:noSuchRegisteredProperty": "also-injected"
                  }
                }
                """;

        FetchEmitTuple t = JsonFetchEmitTuple.fromJson(new StringReader(json));
        Metadata metadata = t.getMetadata();

        // registered curated Property: reconstruct routes through it, so it lands
        assertEquals("attacker-injected", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        // unregistered reserved name: reconstruct's trusted-write branch, still lands
        assertEquals("also-injected", metadata.get("tk:noSuchRegisteredProperty"));
    }
}
