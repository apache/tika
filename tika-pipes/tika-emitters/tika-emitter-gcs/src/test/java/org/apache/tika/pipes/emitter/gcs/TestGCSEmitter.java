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
package org.apache.tika.pipes.emitter.gcs;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * This is meant only for one off development tests with a locally
 * configured GCS instance. Please add unit tests to the appropriate
 * integration test module.
 */
@Disabled("turn into an actual test")
public class TestGCSEmitter {

    @Test
    public void testBasic() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode configNode = mapper.createObjectNode();
        configNode.put("projectId", "my-project");
        configNode.put("bucket", "my-bucket");
        configNode.put("prefix", "output");
        configNode.put("fileExtension", "json");

        ExtensionConfig extensionConfig = new ExtensionConfig("test-gcs", "gcs-emitter",
                mapper.writeValueAsString(configNode));
        GCSEmitter emitter = GCSEmitter.build(extensionConfig);

        List<Metadata> metadataList = new ArrayList<>();
        Metadata m = new Metadata();
        m.set("k1", "v1");
        m.add("k1", "v2");
        m.set("k2", "v3");
        metadataList.add(m);
        emitter.emit("something-or-other/test-out", metadataList, new ParseContext());
    }
}
