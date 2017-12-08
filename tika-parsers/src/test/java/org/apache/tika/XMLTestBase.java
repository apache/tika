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
package org.apache.tika;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.TaggedContentHandler;
import org.apache.tika.sax.TextContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class XMLTestBase extends TikaTest {

    static byte[] injectXML(byte[] input, byte[] toInject) throws IOException {

        int startXML = -1;
        int endXML = -1;
        for (int i = 0; i < input.length; i++) {
            if (input[i] == '<' && i+1 < input.length && input[i+1] == '?') {
                startXML = i;
            }
            if (input[i] == '?' && i+1 < input.length && input[i+1] == '>') {
                endXML = i+1;
                break;
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (startXML > -1 && endXML > -1) {
            bos.write(input, startXML, endXML-startXML+1);
        }
        bos.write(toInject);
        bos.write(input, endXML+1, (input.length-endXML-1));
        return bos.toByteArray();
    }

    static Path injectZippedXMLs(Path original, byte[] toInject, boolean includeSlides) throws IOException {
        ZipFile input = new ZipFile(original.toFile());
        File output = Files.createTempFile("tika-xxe-", ".zip").toFile();
        ZipOutputStream outZip = new ZipOutputStream(new FileOutputStream(output));
        Enumeration<? extends ZipEntry> zipEntryEnumeration = input.entries();
        while (zipEntryEnumeration.hasMoreElements()) {
            ZipEntry entry = zipEntryEnumeration.nextElement();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copy(input.getInputStream(entry), bos);
            byte[] bytes = bos.toByteArray();
            if (entry.getName().endsWith(".xml") &&
                    //don't inject the slides because you'll get a bean exception
                    //Unexpected node
                    (! includeSlides && ! entry.getName().contains("slides/slide"))) {
                bytes = injectXML(bytes, toInject);
            }
            ZipEntry outEntry = new ZipEntry(entry.getName());
            outZip.putNextEntry(outEntry);
            outZip.write(bytes);
            outZip.closeEntry();
        }
        outZip.flush();
        outZip.close();

        return output.toPath();
    }

    static class VulnerableDOMParser extends AbstractParser {

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(MediaType.APPLICATION_XML);
        }

        @Override
        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

            TaggedContentHandler tagged = new TaggedContentHandler(handler);
            try {
                SAXParserFactory saxParserFactory = SAXParserFactory.newInstance(
                        "org.apache.xerces.parsers.SAXParser", this.getClass().getClassLoader());
                SAXParser parser = saxParserFactory.newSAXParser();
                parser.parse( stream,
                        new TextContentHandler(handler,
                                true));
            } catch (ParserConfigurationException e) {
                throw new TikaException("parser config ex", e);
            }

        }
    }

    static class VulnerableSAXParser extends AbstractParser {

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(MediaType.APPLICATION_XML);
        }

        @Override
        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

            TaggedContentHandler tagged = new TaggedContentHandler(handler);
            try {
                SAXParserFactory saxParserFactory = SAXParserFactory.newInstance(
                        "org.apache.xerces.jaxp.SAXParserFactoryImpl", this.getClass().getClassLoader());
                SAXParser parser = saxParserFactory.newSAXParser();
                parser.parse( stream,
                        new TextContentHandler(handler,
                                true));
            } catch (ParserConfigurationException e) {
                throw new TikaException("parser config ex", e);
            }

        }
    }
    static void parse(String testFileName, InputStream is, Parser parser) throws Exception {
        parser.parse(is, new DefaultHandler(), new Metadata(), new ParseContext());
    }
}
