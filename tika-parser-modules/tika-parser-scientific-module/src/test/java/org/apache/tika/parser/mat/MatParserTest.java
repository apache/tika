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
package org.apache.tika.parser.mat;

import static org.junit.Assert.assertEquals;

import org.apache.tika.TikaTest;
import org.junit.Test;

/**
 * Test cases to exercise the {@link MatParser}.
 */
public class MatParserTest extends TikaTest {
    @Test
    public void testParser() throws Exception {

        XMLResult r = getXML("breidamerkurjokull_radar_profiles_2009.mat");
        // Check Metadata
        assertEquals("PCWIN64", r.metadata.get("platform"));
        assertEquals("MATLAB 5.0 MAT-file", r.metadata.get("fileType"));
        assertEquals("IM", r.metadata.get("endian"));
        assertEquals("Thu Feb 21 15:52:49 2013", r.metadata.get("createdOn"));

        // Check Content
        assertContains("<li>[1x909  double array]</li>", r.xml);
        assertContains("<p>c1:[1x1  struct array]</p>", r.xml);
        assertContains("<li>[1024x1  double array]</li>", r.xml);
        assertContains("<p>b1:[1x1  struct array]</p>", r.xml);
        assertContains("<p>a1:[1x1  struct array]</p>", r.xml);
        assertContains("<li>[1024x1261  double array]</li>", r.xml);
        assertContains("<li>[1x1  double array]</li>", r.xml);
        assertContains("</body></html>", r.xml);
    }

    @Test
    public void testParserForText() throws Exception {
        XMLResult r = getXML("test_mat_text.mat", new MatParser());
        assertContains("<p>double:[2x2  double array]</p>", r.xml);
    }
}
