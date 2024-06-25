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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;

@Disabled("turn into an actual test")
public class TestGCSEmitter {

    @Test
    public void testBasic() throws Exception {
        EmitterManager emitterManager = EmitterManager.load(getConfig("tika-config-gcs.xml"));
        Emitter emitter = emitterManager.getEmitter("gcs");
        List<Metadata> metadataList = new ArrayList<>();
        Metadata m = new Metadata();
        m.set("k1", "v1");
        m.add("k1", "v2");
        m.set("k2", "v3");
        metadataList.add(m);
        emitter.emit("something-or-other/test-out", metadataList, new ParseContext());
    }

    private Path getConfig(String configFile) throws URISyntaxException {
        return Paths.get(this
                .getClass()
                .getResource("/config/" + configFile)
                .toURI());
    }
}
