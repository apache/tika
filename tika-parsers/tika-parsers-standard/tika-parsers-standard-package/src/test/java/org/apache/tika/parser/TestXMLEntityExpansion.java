/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.SAXParseException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Tests to confirm defenses against entity expansion attacks.
 */

public class TestXMLEntityExpansion extends XMLTestBase {

    private static final byte[] ENTITY_EXPANSION_BOMB = new String(
            "<!DOCTYPE kaboom [ " + "<!ENTITY a \"1234567890\" > " +
                    "<!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\" >" +
                    "<!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\" > " +
                    "<!ENTITY d \"&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;\" > " +
                    "<!ENTITY e \"&d;&d;&d;&d;&d;&d;&d;&d;&d;&d;\" > " +
                    "<!ENTITY f \"&e;&e;&e;&e;&e;&e;&e;&e;&e;&e;\" > " +
                    "<!ENTITY g \"&f;&f;&f;&f;&f;&f;&f;&f;&f;&f;\" > " +
                    "<!ENTITY h \"&g;&g;&g;&g;&g;&g;&g;&g;&g;&g;\" > " +
                    "<!ENTITY i \"&h;&h;&h;&h;&h;&h;&h;&h;&h;&h;\" > " +
                    "<!ENTITY j \"&i;&i;&i;&i;&i;&i;&i;&i;&i;&i;\" > " +
                    "<!ENTITY k \"&j;&j;&j;&j;&j;&j;&j;&j;&j;&j;\" > " +
                    "<!ENTITY l \"&k;&k;&k;&k;&k;&k;&k;&k;&k;&k;\" > " +
                    "<!ENTITY m \"&l;&l;&l;&l;&l;&l;&l;&l;&l;&l;\" > " +
                    "<!ENTITY n \"&m;&m;&m;&m;&m;&m;&m;&m;&m;&m;\" > " +
                    "<!ENTITY o \"&n;&n;&n;&n;&n;&n;&n;&n;&n;&n;\" > " +
                    "<!ENTITY p \"&o;&o;&o;&o;&o;&o;&o;&o;&o;&o;\" > " +
                    "<!ENTITY q \"&p;&p;&p;&p;&p;&p;&p;&p;&p;&p;\" > " +
                    "<!ENTITY r \"&q;&q;&q;&q;&q;&q;&q;&q;&q;&q;\" > " +
                    "<!ENTITY s \"&r;&r;&r;&r;&r;&r;&r;&r;&r;&r;\" > " + "]> " +
                    "<kaboom>&s;</kaboom>").getBytes(StandardCharsets.UTF_8);

    private static void test(String testFileName, byte[] bytes, Parser parser, ParseContext context)
            throws Exception {
        boolean ex = false;
        try {
            parse(testFileName, new ByteArrayInputStream(bytes), parser, context);
        } catch (SAXParseException e) {
            ex = true;
        } catch (TikaException e) {
            ex = true;
        }
        assertTrue(ex, "should have had an exception");
    }

    //Set a reasonable amount of time as the timeout
    //Make sure that the test apparatus actually works.
    @Disabled
    @Test
    @Timeout(20000)
    public void testVulnerableParser() throws Exception {
        byte[] bytes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><document>blah</document>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] injected = injectXML(bytes, ENTITY_EXPANSION_BOMB);

        Thread thread = new Thread(() -> {
            try {
                parse("injected", new ByteArrayInputStream(injected),
                        new VulnerableSAXParser(), new ParseContext());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
        Thread.sleep(10000);
        assertTrue(thread.isAlive());
        thread.interrupt();

    }

    @Test
    @Timeout(30000)
    public void testProtectedXML() throws Exception {
        byte[] bytes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><document>blah</document>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] injected = injectXML(bytes, ENTITY_EXPANSION_BOMB);

        ParseContext context = new ParseContext();
        for (int i = 0; i < XMLReaderUtils.getPoolSize() * 2; i++) {
            test("default", injected, AUTO_DETECT_PARSER, context);
        }
        context.set(SAXParserFactory.class, XMLReaderUtils.getSAXParserFactory());
        for (int i = 0; i < XMLReaderUtils.getPoolSize() * 2; i++) {
            test("default sax", injected, AUTO_DETECT_PARSER, context);
        }
        String provider = "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl";
        // create a new SAXParserFactory
        SAXParserFactory factory = null;
        try {
            factory = SAXParserFactory.newInstance(provider, null);
        } catch (Exception e) {
            return;
        }
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        context.set(SAXParserFactory.class, factory);
        for (int i = 0; i < XMLReaderUtils.getPoolSize() * 2; i++) {
            test("built-in SAX", injected, AUTO_DETECT_PARSER, context);
        }
    }

    @Test
    public void testDOM() throws Exception {
        byte[] bytes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><document>blah</document>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] injected = injectXML(bytes, ENTITY_EXPANSION_BOMB);
        for (int i = 0; i < XMLReaderUtils.getPoolSize() * 6; i++) {
            try {
                XMLReaderUtils.buildDOM(new ByteArrayInputStream(injected));
                fail("should never parse!");
            } catch (SAXParseException e) {
                //can't rely on message content with different xml parsers/java versions
                //assertTrue(e.getMessage() != null && e.getMessage().contains("entity
                // expansions"));
            }
        }
    }
}
