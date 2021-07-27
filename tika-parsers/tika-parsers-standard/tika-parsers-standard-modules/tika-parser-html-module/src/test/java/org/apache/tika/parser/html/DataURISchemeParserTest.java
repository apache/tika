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

package org.apache.tika.parser.html;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.mime.MediaType;

public class DataURISchemeParserTest extends TikaTest {
    DataURISchemeUtil dataURISchemeUtil = new DataURISchemeUtil();

    @Test
    public void testEmpty() throws Exception {
        DataURIScheme dataURIScheme = dataURISchemeUtil.parse("data:,");
        assertFalse(dataURIScheme.isBase64());
        assertNull(dataURIScheme.getMediaType());
        assertEquals(-1, dataURIScheme.getInputStream().read());
    }

    @Test
    public void testNewlines() throws Exception {
        String data = "data:image/png;base64,R0lG\nODdh";
        DataURIScheme dataURIScheme = dataURISchemeUtil.parse(data);
        assertTrue(dataURIScheme.isBase64());
        assertEquals(MediaType.image("png"), dataURIScheme.getMediaType());

        String expected = "data:image/png;base64,R0lGODdh";
        assertEquals(dataURISchemeUtil.parse(expected), dataURISchemeUtil.parse(data));

    }

    @Test
    public void testBackslashNewlines() throws Exception {
        //like you'd have in a css fragment
        String data = "data:image/png;base64,R0lG\\\nODdh";
        DataURIScheme dataURIScheme = dataURISchemeUtil.parse(data);
        assertTrue(dataURIScheme.isBase64());
        assertEquals(MediaType.image("png"), dataURIScheme.getMediaType());

        String expected = "data:image/png;base64,R0lGODdh";
        assertEquals(dataURISchemeUtil.parse(expected), dataURISchemeUtil.parse(data));
    }

    @Test
    public void testUTF8() throws Exception {
        String utf8 = "\u0628\u0631\u0646\u0633\u062A\u0648\u0646";
        String data = "data:text/plain;charset=UTF-8;page=21,the%20data:" + utf8;
        DataURIScheme dataURIScheme = dataURISchemeUtil.parse(data);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(dataURIScheme.getInputStream(), bos);
        assertContains(utf8, new String(bos.toByteArray(), StandardCharsets.UTF_8));
    }
}
