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
package org.apache.tika.pipes.emitter.solr;


import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;

@Ignore("requires solr to be up and running")
public class TestBasic {

    @Test
    public void testBasic() throws Exception {
        TikaConfig tikaConfig = new TikaConfig(
                TestBasic.class.getResourceAsStream("/tika-config-simple-emitter.xml"));
        EmitterManager emitterManager = EmitterManager.load(
                Paths.get(TestBasic.class.getResource("/tika-config-simple-emitter.xml").toURI())
        );
        Emitter emitter = emitterManager.getEmitter("solr1");
        List<Metadata> metadataList = getParentChild(tikaConfig, "id1", 2);

        emitter.emit("1", metadataList);
    }

    @Test
    public void testBatch() throws Exception {
        TikaConfig tikaConfig = new TikaConfig(
                TestBasic.class.getResourceAsStream("/tika-config-simple-emitter.xml"));
        EmitterManager emitterManager = EmitterManager.load(
                Paths.get(TestBasic.class.getResource("/tika-config-simple-emitter.xml").toURI())
        );
        Emitter emitter = emitterManager.getEmitter("solr2");
        List<EmitData> emitData = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            List<Metadata> metadataList = getParentChild(tikaConfig, "batch_" + i, 4);
            emitData.add(new EmitData(new EmitKey(emitter.getName(),
                    "batch_" + i), metadataList));
        }
        emitter.emit(emitData);
    }

    private List<Metadata> getParentChild(TikaConfig tikaConfig, String id, int numChildren)
            throws TikaException {
        List<Metadata> metadataList = new ArrayList<>();
        MetadataFilter filter = tikaConfig.getMetadataFilter();

        Metadata m1 = new Metadata();
        m1.set("id", id);
        m1.set(Metadata.CONTENT_LENGTH, "314159");
        m1.set(TikaCoreProperties.TIKA_CONTENT, "the quick brown");
        m1.set(TikaCoreProperties.TITLE, "this is the first title");
        m1.add(TikaCoreProperties.CREATOR, "firstAuthor");
        m1.add(TikaCoreProperties.CREATOR, "secondAuthor");
        filter.filter(m1);
        metadataList.add(m1);
        for (int i = 1; i < numChildren; i++) {
            Metadata m2 = new Metadata();
            m2.set(TikaCoreProperties.EMBEDDED_RESOURCE_PATH, "/path_to_this.txt");
            m2.set(TikaCoreProperties.TIKA_CONTENT, "fox jumped over the lazy " + i);
            filter.filter(m2);
            metadataList.add(m2);
        }
        return metadataList;
    }

}
