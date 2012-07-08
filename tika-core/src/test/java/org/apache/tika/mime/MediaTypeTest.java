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
package org.apache.tika.mime;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import static java.util.Collections.singletonMap;

public class MediaTypeTest extends TestCase {

    public void testBasics() {
        assertEquals(
                "application/octet-stream",
                new MediaType("application", "octet-stream").toString());

        assertEquals(
                "text/plain",
                new MediaType("text", "plain").toString());

        Map<String, String> parameters = new HashMap<String, String>();
        assertEquals(
                "text/plain",
                new MediaType("text", "plain", parameters).toString());

        parameters.put("charset", "UTF-8");
        assertEquals(
                "text/plain; charset=UTF-8",
                new MediaType("text", "plain", parameters).toString());

        parameters.put("x-eol-style", "crlf");
        assertEquals(
                "text/plain; charset=UTF-8; x-eol-style=crlf",
                new MediaType("text", "plain", parameters).toString());
    }

    public void testLowerCase() {
        assertEquals(
                "text/plain",
                new MediaType("TEXT", "PLAIN").toString());
        assertEquals(
                "text/plain",
                new MediaType("Text", "Plain").toString());

        Map<String, String> parameters = new HashMap<String, String>();
        assertEquals(
                "text/plain",
                new MediaType("text", "PLAIN", parameters).toString());

        parameters.put("CHARSET", "UTF-8");
        assertEquals(
                "text/plain; charset=UTF-8",
                new MediaType("TEXT", "plain", parameters).toString());

        parameters.put("X-Eol-Style", "crlf");
        assertEquals(
                "text/plain; charset=UTF-8; x-eol-style=crlf",
                new MediaType("TeXt", "PlAiN", parameters).toString());
    }

    public void testTrim() {
        assertEquals(
                "text/plain",
                new MediaType(" text ", " plain ").toString());
        assertEquals(
                "text/plain",
                new MediaType("\ttext", "plain\t").toString());

        Map<String, String> parameters = new HashMap<String, String>();
        assertEquals(
                "text/plain",
                new MediaType("text\r\n", " \tplain", parameters).toString());

        parameters.put(" charset", "UTF-8");
        assertEquals(
                "text/plain; charset=UTF-8",
                new MediaType("\n\ntext", "plain \r", parameters).toString());

        parameters.put("\r\n\tx-eol-style  \t", "crlf");
        assertEquals(
                "text/plain; charset=UTF-8; x-eol-style=crlf",
            new MediaType("    text", "\tplain ", parameters).toString());
    }

    public void testQuote() {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("a", " value with spaces ");
        parameters.put("b", "text/plain");
        parameters.put("c", "()<>@,;:\\\"/[]?=");
        assertEquals(
                "text/plain; a=\" value with spaces \"; b=\"text\\/plain\""
                + "; c=\"\\(\\)\\<\\>\\@\\,\\;\\:\\\\\\\"\\/\\[\\]\\?\\=\"",
                new MediaType("text", "plain", parameters).toString());
    }
    
    /**
     * @since TIKA-121
     */
    public void testParseWithParams() {
        String mimeStringWithParams = "text/html;charset=UTF-8;foo=bar;foo2=bar2";

        MediaType type = MediaType.parse(mimeStringWithParams);
        assertNotNull(type);
        assertNotNull(type.getParameters());
        assertNotNull(type.getParameters().keySet());
        assertEquals(3, type.getParameters().keySet().size());
        boolean gotCharset = false, gotFoo = false, gotFoo2 = false;
        for (String param : type.getParameters().keySet()) {
            if (param.equals("charset")) {
                gotCharset = true;
            } else if (param.equals("foo")) {
                gotFoo = true;
            } else if (param.equals("foo2")) {
                gotFoo2 = true;
            }
        }
        assertTrue(gotCharset && gotFoo && gotFoo2);
    }

    /**
     * Per http://tools.ietf.org/html/rfc2045#section-5.1, charset can be in quotes
     */
    public void testParseWithParamsAndQuotedCharset() {
        // Typical case, with a quoted charset
        String mimeStringWithParams = "text/html;charset=\"UTF-8\"";

        MediaType type = MediaType.parse(mimeStringWithParams);
        assertNotNull(type);
        assertEquals(singletonMap("charset", "UTF-8"), type.getParameters());
        
        // Complex case, with various different quoted and un-quoted forms
        mimeStringWithParams = "text/html;charset=\'UTF-8\';test=\"true\";unquoted=here";

        type = MediaType.parse(mimeStringWithParams);
        assertNotNull(type);
        assertEquals(3, type.getParameters().size());
        assertEquals("UTF-8", type.getParameters().get("charset"));
        assertEquals("true", type.getParameters().get("test"));
        assertEquals("here", type.getParameters().get("unquoted"));
    }

    /**
     * @since TIKA-121
     */
    public void testParseNoParams() {
        String mimeStringNoParams = "text/html";

        MediaType type = MediaType.parse(mimeStringNoParams);
        assertNotNull(type);
        assertNotNull(type.getParameters());
        assertNotNull(type.getParameters().keySet());
        assertEquals(0, type.getParameters().keySet().size());
    }

    /**
     * @since TIKA-121
     */
    public void testParseNoParamsWithSemi() {
        String mimeStringNoParamsWithSemi = "text/html;";
        MediaType type = MediaType.parse(mimeStringNoParamsWithSemi);
        assertNotNull(type);
        assertNotNull(type.getParameters());
        assertNotNull(type.getParameters().keySet());
        assertEquals(0, type.getParameters().keySet().size());
    }

    /**
     * TIKA-349
     */
    public void testOddParameters() {
        assertEquals(
                "text/html; charset=UTF-8",
                MediaType.parse("text/html;; charset=UTF-8").toString());
        assertEquals(
                "text/html; charset=UTF-8",
                MediaType.parse("text/html;; charset=UTF-8").toString());
        assertEquals(
                "text/html; charset=UTF-8",
                MediaType.parse("text/html;; charset=\"UTF-8\"").toString());
        assertEquals(
                "text/html; charset=UTF-8",
                MediaType.parse("text/html;; charset=\"UTF-8").toString());
    }

}
