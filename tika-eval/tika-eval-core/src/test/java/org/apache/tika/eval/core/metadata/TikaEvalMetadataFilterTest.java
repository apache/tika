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
package org.apache.tika.eval.core.metadata;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.DefaultMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;

public class TikaEvalMetadataFilterTest {

    @Test
    public void testBasic() throws Exception {
        for (MetadataFilter filter : new MetadataFilter[]{new TikaEvalMetadataFilter(),
                //make sure that the TikaEvalMetadataFilter is loaded automatically
                new DefaultMetadataFilter()}) {
            Metadata metadata = new Metadata();
            String content = "the quick brown fox, Zothro 1234 1235, jumped over the lazy dog";
            metadata.set(TikaCoreProperties.TIKA_CONTENT, content);

            filter.filter(metadata);
            assertEquals("eng", metadata.get(TikaEvalMetadataFilter.LANGUAGE));
            assertEquals(12, (int) metadata.getInt(TikaEvalMetadataFilter.NUM_TOKENS));
            assertEquals(11, (int) metadata.getInt(TikaEvalMetadataFilter.NUM_UNIQUE_TOKENS));
            assertEquals(10, (int) metadata.getInt(TikaEvalMetadataFilter.NUM_ALPHA_TOKENS));
            assertEquals(9, (int) metadata.getInt(TikaEvalMetadataFilter.NUM_UNIQUE_ALPHA_TOKENS));


            assertEquals(0.0999,
                    Double.parseDouble(metadata.get(TikaEvalMetadataFilter.OUT_OF_VOCABULARY)),
                    0.1);
            assertEquals("eng", metadata.get(TikaEvalMetadataFilter.LANGUAGE));

            assertEquals(0.0196,
                    Double.parseDouble(metadata.get(TikaEvalMetadataFilter.LANGUAGE_CONFIDENCE)),
                    0.1);
        }
    }
}
