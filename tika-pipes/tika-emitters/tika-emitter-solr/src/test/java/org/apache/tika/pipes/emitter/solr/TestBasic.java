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


import org.apache.tika.config.TikaConfig;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

@Ignore("requires solr to be up and running")
public class TestBasic {

    @Test
    public void testBasic() throws Exception {
        TikaConfig tikaConfig = new TikaConfig(
                TestBasic.class.getResourceAsStream("/tika-config-simple-emitter.xml"));
        Emitter emitter = tikaConfig.getEmitterManager().getEmitter("solr1");
        List<Metadata> metadataList = new ArrayList<>();
        Metadata m1 = new Metadata();
        m1.set(Metadata.CONTENT_LENGTH, "314159");
        m1.set(TikaCoreProperties.TIKA_CONTENT, "the quick brown");
        m1.set(TikaCoreProperties.TITLE, "this is the first title");
        m1.add(TikaCoreProperties.CREATOR, "firstAuthor");
        m1.add(TikaCoreProperties.CREATOR, "secondAuthor");

        Metadata m2 = new Metadata();
        m2.set(TikaCoreProperties.EMBEDDED_RESOURCE_PATH, "/path_to_this.txt");
        m2.set(TikaCoreProperties.TIKA_CONTENT, "fox jumped over the lazy");
        MetadataFilter filter = tikaConfig.getMetadataFilter();
        filter.filter(m1);
        filter.filter(m2);
        metadataList.add(m1);
        metadataList.add(m2);

        emitter.emit("1", metadataList);
    }
}
