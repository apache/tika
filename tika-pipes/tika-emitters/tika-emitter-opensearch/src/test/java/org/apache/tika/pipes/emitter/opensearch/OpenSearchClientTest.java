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
package org.apache.tika.pipes.emitter.opensearch;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;

public class OpenSearchClientTest extends TikaTest {

    @Test
    public void testSerialization() throws Exception {
        Metadata metadata = new Metadata();
        metadata.add("authors", "author1");
        metadata.add("authors", "author2");
        metadata.add("title", "title1");
        for (OpenSearchEmitter.AttachmentStrategy strategy :
                OpenSearchEmitter.AttachmentStrategy.values()) {
            String json = OpenSearchClient.metadataToJsonContainerInsert(metadata,
                    strategy);
            assertContains("author1", json);
            assertContains("author2", json);
            assertContains("authors", json);
            assertContains("title1", json);
        }
        for (OpenSearchEmitter.AttachmentStrategy strategy :
                OpenSearchEmitter.AttachmentStrategy.values()) {
            String json = OpenSearchClient.metadataToJsonEmbeddedInsert(metadata, strategy,
                    "myEmitKey", OpenSearchEmitter.DEFAULT_EMBEDDED_FILE_FIELD_NAME);
            assertContains("author1", json);
            assertContains("author2", json);
            assertContains("authors", json);
            assertContains("title1", json);
        }

    }
}
