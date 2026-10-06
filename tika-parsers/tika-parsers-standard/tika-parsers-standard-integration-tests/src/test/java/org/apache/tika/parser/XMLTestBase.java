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
package org.apache.tika.parser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.xml.secure.SecureSAXParserFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.TaggedContentHandler;
import org.apache.tika.sax.TextContentHandler;

public class XMLTestBase extends TikaTest {

    static final byte[] ENTITY_EXPANSION_BOMB = new String(
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

    static byte[] injectXML(byte[] input, byte[] toInject) throws IOException {

        int startXML = -1;
        int endXML = -1;
        for (int i = 0; i < input.length; i++) {
            if (input[i] == '<' && i + 1 < input.length && input[i + 1] == '?') {
                startXML = i;
            }
            if (input[i] == '?' && i + 1 < input.length && input[i + 1] == '>') {
                endXML = i + 1;
                break;
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (startXML > -1 && endXML > -1) {
            bos.write(input, startXML, endXML - startXML + 1);
        }
        bos.write(toInject);
        bos.write(input, endXML + 1, (input.length - endXML - 1));
        return bos.toByteArray();
    }

    // XML parts of zip containers: OOXML/ODF/EPUB parts, OOXML relationships, XPS pages
    static boolean isXmlEntry(String name) {
        return name.endsWith(".xml") || name.endsWith(".rels") || name.endsWith(".fpage");
    }

    static Path injectZippedXMLs(Path original, byte[] toInject) throws IOException {
        Path output = Files.createTempFile("tika-xxe-", ".zip");
        try (ZipFile input = new ZipFile(original.toFile());
                ZipOutputStream outZip = new ZipOutputStream(Files.newOutputStream(output))) {
            Enumeration<? extends ZipEntry> zipEntryEnumeration = input.entries();
            while (zipEntryEnumeration.hasMoreElements()) {
                ZipEntry entry = zipEntryEnumeration.nextElement();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copy(input.getInputStream(entry), bos);
                byte[] bytes = bos.toByteArray();
                if (isXmlEntry(entry.getName())) {
                    bytes = injectXML(bytes, toInject);
                }
                outZip.putNextEntry(new ZipEntry(entry.getName()));
                outZip.write(bytes);
                outZip.closeEntry();
            }
        }
        return output;
    }

    static void parse(String testFileName, TikaInputStream is, Parser parser, ParseContext context)
            throws Exception {
        parser.parse(is, new DefaultHandler(), new Metadata(), context);
    }

    static class VulnerableDOMParser implements Parser {

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(MediaType.APPLICATION_XML);
        }

        @Override
        public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {

            TaggedContentHandler tagged = new TaggedContentHandler(handler);
            try {
                SAXParserFactory saxParserFactory = SecureSAXParserFactory
                        .newInstance("org.apache.xerces.parsers.SAXParser",
                                this.getClass().getClassLoader());
                SAXParser parser = saxParserFactory.newSAXParser();
                parser.parse(stream, new TextContentHandler(handler, true));
            } catch (ParserConfigurationException e) {
                throw new TikaException("parser config ex", e);
            }

        }
    }

    static class VulnerableSAXParser implements Parser {

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(MediaType.APPLICATION_XML);
        }

        @Override
        public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {

            TaggedContentHandler tagged = new TaggedContentHandler(handler);
            try {
                SAXParserFactory saxParserFactory = SecureSAXParserFactory
                        .newInstance();
                SAXParser parser = saxParserFactory.newSAXParser();
                parser.parse(stream, new TextContentHandler(handler, true));
            } catch (ParserConfigurationException e) {
                throw new TikaException("parser config ex", e);
            }

        }
    }
}
