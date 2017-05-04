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

package org.apache.tika.parser.apple;


import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.pdf.PDFParser;
import org.junit.Test;

public class AppleSingleFileParserTest extends TikaTest {

    @Test
    public void testBasic() throws Exception {
        List<Metadata> list = getRecursiveMetadata("testAppleSingleFile.pdf");
        assertEquals(2, list.size());
        assertContains(AppleSingleFileParser.class.getName(),
                Arrays.asList(list.get(0).getValues("X-Parsed-By")));
        assertContains(PDFParser.class.getName(),
                Arrays.asList(list.get(1).getValues("X-Parsed-By")));
        assertContains("Hello World", list.get(1).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("fltsyllabussortie2rev1_2.pdf", list.get(1).get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME));
    }
}
