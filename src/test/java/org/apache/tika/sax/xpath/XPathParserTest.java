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
package org.apache.tika.sax.xpath;

import junit.framework.TestCase;

public class XPathParserTest extends TestCase {

    private static final String NS = "test namespace";

    private XPathParser parser;

    protected void setUp() {
        parser = new XPathParser();
        parser.addPrefix(null, null);
        parser.addPrefix("prefix", NS);
    }

    public void testText() {
        Matcher matcher = parser.parse("/text()");
        assertTrue(matcher.matchesText());
        assertFalse(matcher.matchesElement());
        assertFalse(matcher.matchesAttribute(NS, "name"));
        assertEquals(Matcher.FAIL, matcher.descend(NS, "name"));
    }

    public void testAnyAttribute() {
        Matcher matcher = parser.parse("/@*");
        assertFalse(matcher.matchesText());
        assertFalse(matcher.matchesElement());
        assertTrue(matcher.matchesAttribute(null, "name"));
        assertTrue(matcher.matchesAttribute(NS, "name"));
        assertTrue(matcher.matchesAttribute(NS, "eman"));
        assertEquals(Matcher.FAIL, matcher.descend(NS, "name"));
    }

    public void testNamedAttribute() {
        Matcher matcher = parser.parse("/@name");
        assertFalse(matcher.matchesText());
        assertFalse(matcher.matchesElement());
        assertTrue(matcher.matchesAttribute(null, "name"));
        assertFalse(matcher.matchesAttribute(NS, "name"));
        assertFalse(matcher.matchesAttribute(NS, "eman"));
        assertEquals(Matcher.FAIL, matcher.descend(NS, "name"));
    }

    public void testPrefixedAttribute() {
        Matcher matcher = parser.parse("/@prefix:name");
        assertFalse(matcher.matchesText());
        assertFalse(matcher.matchesElement());
        assertFalse(matcher.matchesAttribute(null, "name"));
        assertTrue(matcher.matchesAttribute(NS, "name"));
        assertFalse(matcher.matchesAttribute(NS, "eman"));
        assertEquals(Matcher.FAIL, matcher.descend(NS, "name"));
    }

    public void testAnyElement() {
        Matcher matcher = parser.parse("/*");
        assertFalse(matcher.matchesText());
        assertFalse(matcher.matchesElement());
        assertFalse(matcher.matchesAttribute(null, "name"));
        assertFalse(matcher.matchesAttribute(NS, "name"));
        assertFalse(matcher.matchesAttribute(NS, "eman"));
        matcher = matcher.descend(NS, "name");
        assertFalse(matcher.matchesText());
        assertTrue(matcher.matchesElement());
        assertFalse(matcher.matchesAttribute(null, "name"));
        assertFalse(matcher.matchesAttribute(NS, "name"));
        assertFalse(matcher.matchesAttribute(NS, "eman"));
        assertEquals(Matcher.FAIL, matcher.descend(NS, "name"));
    }

    public void testNamedElement() {
        Matcher matcher = parser.parse("/name");
        assertFalse(matcher.matchesText());
        assertFalse(matcher.matchesElement());
        assertFalse(matcher.matchesAttribute(null, "name"));
        assertFalse(matcher.matchesAttribute(NS, "name"));
        assertFalse(matcher.matchesAttribute(NS, "eman"));
        assertEquals(Matcher.FAIL, matcher.descend(NS, "name"));
        assertEquals(Matcher.FAIL, matcher.descend(null, "enam"));
        matcher = matcher.descend(null, "name");
        assertFalse(matcher.matchesText());
        assertTrue(matcher.matchesElement());
        assertFalse(matcher.matchesAttribute(null, "name"));
        assertFalse(matcher.matchesAttribute(NS, "name"));
        assertFalse(matcher.matchesAttribute(NS, "eman"));
    }

    public void testPrefixedElement() {
        Matcher matcher = parser.parse("/prefix:name");
        assertFalse(matcher.matchesText());
        assertFalse(matcher.matchesElement());
        assertFalse(matcher.matchesAttribute(null, "name"));
        assertFalse(matcher.matchesAttribute(NS, "name"));
        assertFalse(matcher.matchesAttribute(NS, "eman"));
        assertEquals(Matcher.FAIL, matcher.descend(null, "name"));
        assertEquals(Matcher.FAIL, matcher.descend(NS, "enam"));
        matcher = matcher.descend(NS, "name");
        assertFalse(matcher.matchesText());
        assertTrue(matcher.matchesElement());
        assertFalse(matcher.matchesAttribute(null, "name"));
        assertFalse(matcher.matchesAttribute(NS, "name"));
        assertFalse(matcher.matchesAttribute(NS, "eman"));
    }

}
