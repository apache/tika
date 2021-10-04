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
package org.apache.tika.parser.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPMM;

public class JXLTest extends TikaTest {

    @Test
    public void testBasicXMP() throws Exception {
        Metadata metadata = getXML("testJXL_ISOBMFF.jxl").metadata;
        assertEquals("Unknown Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("adobe:docid:photoshop:162ca2dc-6a89-9c46-8fcc-3a7f0e6deb18",
                metadata.get(XMPMM.DOCUMENTID));
    }
}
