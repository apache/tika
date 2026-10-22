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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;

/** pipes.trustedRequests: a fork reads its requests' parseContext as trusted config. */
public class TrustedRequestsWireTest {

    @Test
    public void testParseContextTrustFollowsTheFork() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("pdf-parser", "{\"maxMainMemoryBytes\": 1000}");
        byte[] bytes = JsonPipesIpc.toBytes(new FetchEmitTuple("id", new FetchKey("f", "k"),
                new EmitKey("e", "k"), new Metadata(), context,
                FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertFalse(JsonPipesIpc.fromBytes(bytes, FetchEmitTuple.class, false)
                .getParseContext().getJsonConfig("pdf-parser").trusted());
        assertTrue(JsonPipesIpc.fromBytes(bytes, FetchEmitTuple.class, true)
                .getParseContext().getJsonConfig("pdf-parser").trusted());
    }
}
