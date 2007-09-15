/**
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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import junit.framework.TestCase;

public class MimeTypesUtilsTest extends TestCase {

    public void test() throws MalformedURLException {
        URL u = new URL("http://mydomain.com/x.pdf?x=y");
        File f = new File("/a/b/c/x.pdf");

        assertEquals("application/pdf",MimeTypesUtils.getMimeType("x.pdf"));
        assertEquals("application/pdf",MimeTypesUtils.getMimeType(u));
        assertEquals("application/pdf",MimeTypesUtils.getMimeType(f));
        assertEquals("text/plain",MimeTypesUtils.getMimeType("x.txt"));
        assertEquals("text/html",MimeTypesUtils.getMimeType("x.htm"));
        assertEquals("text/html",MimeTypesUtils.getMimeType("x.html"));
        assertEquals("application/xhtml+xml",MimeTypesUtils.getMimeType("x.xhtml"));
        assertEquals("application/xml",MimeTypesUtils.getMimeType("x.xml"));
        assertEquals("application/msword",MimeTypesUtils.getMimeType("x.doc"));
        assertEquals("application/vnd.ms-powerpoint",MimeTypesUtils.getMimeType("x.ppt"));
        assertEquals("application/vnd.ms-excel",MimeTypesUtils.getMimeType("x.xls"));
        assertEquals("application/zip",MimeTypesUtils.getMimeType("x.zip"));
        assertEquals("application/vnd.oasis.opendocument.text",MimeTypesUtils.getMimeType("x.odt"));
        assertEquals("application/octet-stream",MimeTypesUtils.getMimeType("x.xyz"));
    }
}
