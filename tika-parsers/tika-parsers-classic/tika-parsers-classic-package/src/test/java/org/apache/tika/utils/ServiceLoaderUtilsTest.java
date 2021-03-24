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

package org.apache.tika.utils;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.Parser;

public class ServiceLoaderUtilsTest extends TikaTest {
    @Test
    public void testOrdering() throws Exception {
        //make sure that non Tika parsers come last
        //which means that they'll overwrite Tika parsers and
        //be preferred.
        DefaultParser defaultParser = new DefaultParser();
        int vorbisIndex = -1;
        int fictIndex = -1;
        int dcxmlIndex = -1;
        int i = 0;
        for (Parser p : defaultParser.getAllComponentParsers()) {
            if ("class org.gagravarr.tika.VorbisParser".equals(p.getClass().toString())) {
                vorbisIndex = i;
            }
            if ("class org.apache.tika.parser.xml.FictionBookParser"
                    .equals(p.getClass().toString())) {
                fictIndex = i;
            }
            if ("class org.apache.tika.parser.xml.DcXMLParser".equals(p.getClass().toString())) {
                dcxmlIndex = i;
            }
            i++;
        }

        assertNotEquals(vorbisIndex, fictIndex);
        assertNotEquals(fictIndex, dcxmlIndex);
        assertTrue(vorbisIndex < fictIndex);
        assertTrue(fictIndex > dcxmlIndex);
    }
}
