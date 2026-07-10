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
package org.apache.tika.parser.xmp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class XmpSaxFlattenerTest {

    private static final String PACKET =
            "<x:xmpmeta xmlns:x='adobe:ns:meta/' x:xmptk='Test 1.0'>"
          + "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'"
          + "         xmlns:xmp='http://ns.adobe.com/xap/1.0/'"
          + "         xmlns:xmpMM='http://ns.adobe.com/xap/1.0/mm/'"
          + "         xmlns:stRef='http://ns.adobe.com/xap/1.0/sType/ResourceRef#'"
          + "         xmlns:stEvt='http://ns.adobe.com/xap/1.0/sType/ResourceEvent#'"
          + "         xmlns:tiff='http://ns.adobe.com/tiff/1.0/'"
          + "         xmlns:dc='http://purl.org/dc/elements/1.1/'>"
          + "<rdf:Description rdf:about='' tiff:Make='Canon'>"
          + "  <xmp:CreateDate>2020-01-02T03:04:05Z</xmp:CreateDate>"
          + "  <dc:creator><rdf:Seq><rdf:li>Alice</rdf:li><rdf:li>Bob</rdf:li></rdf:Seq></dc:creator>"
          + "  <dc:title><rdf:Alt><rdf:li xml:lang='x-default'>Hello</rdf:li></rdf:Alt></dc:title>"
          + "  <xmpMM:DerivedFrom rdf:parseType='Resource'>"
          + "    <stRef:documentID>docid-123</stRef:documentID>"
          + "  </xmpMM:DerivedFrom>"
          + "  <xmpMM:History><rdf:Seq>"
          + "    <rdf:li rdf:parseType='Resource'><stEvt:action>created</stEvt:action></rdf:li>"
          + "    <rdf:li rdf:parseType='Resource'><stEvt:action>saved</stEvt:action></rdf:li>"
          + "  </rdf:Seq></xmpMM:History>"
          + "</rdf:Description></rdf:RDF></x:xmpmeta>";

    private List<XmpProperty> flatten() throws Exception {
        return new XmpSaxFlattener().flatten(PACKET.getBytes(UTF_8));
    }

    private static String get(List<XmpProperty> l, String path) {
        List<String> v = new ArrayList<>();
        for (XmpProperty p : l) {
            if (p.path.equals(path)) {
                v.add(p.value);
            }
        }
        return v.isEmpty() ? null : String.join("|", v);
    }

    @Test
    public void testSimpleAndCompact() throws Exception {
        List<XmpProperty> l = flatten();
        assertEquals("2020-01-02T03:04:05Z", get(l, "xmp:CreateDate"));
        assertEquals("Canon", get(l, "tiff:Make"));          // compact attribute form
    }

    @Test
    public void testArrayIndices() throws Exception {
        List<XmpProperty> l = flatten();
        assertEquals("Alice", get(l, "dc:creator[1]"));
        assertEquals("Bob", get(l, "dc:creator[2]"));
    }

    @Test
    public void testLangAlt() throws Exception {
        List<XmpProperty> l = flatten();
        assertEquals("Hello", get(l, "dc:title[1]"));
        assertEquals("x-default", get(l, "dc:title[1]/xml:lang"));
    }

    @Test
    public void testStructAndHistory() throws Exception {
        List<XmpProperty> l = flatten();
        assertEquals("docid-123", get(l, "xmpMM:DerivedFrom/stRef:documentID"));
        assertEquals("created", get(l, "xmpMM:History[1]/stEvt:action"));
        assertEquals("saved", get(l, "xmpMM:History[2]/stEvt:action"));
    }

    @Test
    public void testNamespaceUriTracked() throws Exception {
        List<XmpProperty> l = flatten();
        for (XmpProperty p : l) {
            if (p.path.equals("xmp:CreateDate")) {
                assertEquals("http://ns.adobe.com/xap/1.0/", p.namespaceURI);
            }
            if (p.path.startsWith("dc:creator")) {
                assertEquals("http://purl.org/dc/elements/1.1/", p.namespaceURI);
            }
        }
    }

    @Test
    public void testToolkitAndAboutDropped() throws Exception {
        List<XmpProperty> l = flatten();
        // x:xmptk (adobe:ns:meta/) and rdf:about are not properties
        assertNull(get(l, "x:xmptk"));
        for (XmpProperty p : l) {
            assertTrue(!p.path.contains("xmptk") && !p.path.contains("about"),
                    "unexpected leaf: " + p);
        }
    }
}
