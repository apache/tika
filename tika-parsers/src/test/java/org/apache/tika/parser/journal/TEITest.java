/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.parser.journal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.tika.TikaTest;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.junit.Test;

public class TEITest extends TikaTest {


    @Test
    public void testBasic() throws Exception {
        TEIDOMParser teiParser = new TEIDOMParser();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = getResourceAsStream("/test-documents/testTEI.xml")) {
            IOUtils.copy(is, bos);
        }
        String xml = new String (bos.toByteArray(), StandardCharsets.UTF_8);
        Metadata metadata = teiParser.parse(xml, new ParseContext());
        assertEquals("Montbonnot Saint-Martin, Montbonnot Saint-Martin, Montbonnot Saint-Martin, " +
                "Montbonnot Saint-Martin, null 38330, 38330, 38330, 38330 " +
                "France, France, France, France ", metadata.get("Address"));
        String[] keywords = new String[]{
                "F22 [Analysis of Algorithms and Problem Complexity]: Nonnumerical Algorithms and Problems\u2014Sequencing",
                "and scheduling; D41 [Operating Systems]: Process management\u2014Scheduling, Concurrency",
                "Keywords",
                "Parallel Computing, Algorithms, Scheduling, Parallel Tasks,",
                "Moldable Tasks, Bi-criteria"
        };
        assertArrayEquals(keywords, metadata.getValues("Keyword"));
        assertEquals("Pierre-François  Dutot 1 Lionel  Eyraud 1 Grégory  Gr´ 1 Grégory  Mouní 1 Denis  Trystram 1 ",
                metadata.get("Authors"));
        assertEquals("Bi-criteria Algorithm for Scheduling Jobs on Cluster Platforms *",
                metadata.get("Title"));
        assertEquals("1 ID-IMAG ID-IMAG ID-IMAG ID-IMAG", metadata.get("Affiliation"));
        assertEquals("[Affiliation {orgName=ID-IMAG ID-IMAG ID-IMAG ID-IMAG , " +
                        "address=Montbonnot Saint-Martin, Montbonnot Saint-Martin, Montbonnot Saint-Martin, Montbonnot Saint-Martin, " +
                        "null 38330, 38330, 38330, 38330 France, France, France, France}" +
                        "[Affiliation {orgName=ID-IMAG ID-IMAG ID-IMAG ID-IMAG , " +
                        "address=Montbonnot Saint-Martin, Montbonnot Saint-Martin, Montbonnot Saint-Martin, Montbonnot Saint-Martin, " +
                        "null 38330, 38330, 38330, 38330 France, France, France, France}]",
                metadata.get("FullAffiliations"));
    }
}
