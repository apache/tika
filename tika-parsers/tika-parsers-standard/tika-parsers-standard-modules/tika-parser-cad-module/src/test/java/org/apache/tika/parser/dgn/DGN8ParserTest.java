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
package org.apache.tika.parser.dgn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;

public class DGN8ParserTest extends TikaTest {
    /**
     * Try with a simple file
     */
    @Test
    public void testBasics() throws Exception {
        Metadata metadata = getXML("testDGN8.dgn").metadata;
        assertEquals("John.Frampton", metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("MicroStation v8.11.0.0", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertContains("org.apache.tika.parser.dgn.DGN8Parser",
                Arrays.asList(metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY)));

    }

}
