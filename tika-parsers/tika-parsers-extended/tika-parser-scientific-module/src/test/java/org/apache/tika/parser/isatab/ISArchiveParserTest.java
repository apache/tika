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
package org.apache.tika.parser.isatab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

public class ISArchiveParserTest extends TikaTest {

    @Test
    public void testParseArchive() throws Exception {
        String path = "/test-documents/testISATab_BII-I-1/s_BII-S-1.txt";

        Parser parser = new ISArchiveParser(
                ISArchiveParserTest.class.getResource("/test-documents/testISATab_BII-I-1/").toURI()
                        .getPath());
        //Parser parser = new AutoDetectParser();

        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "s_BII-S-1.txt");
        ParseContext context = new ParseContext();
        try (TikaInputStream tis = getResourceAsStream(path)) {
            parser.parse(tis, handler, metadata, context);
        }

        // INVESTIGATION
        assertEquals("BII-I-1", metadata.get("Investigation Identifier"),
                "Invalid Investigation Identifier");
        assertEquals("Growth control of the eukaryote cell: a systems biology study in yeast",
                metadata.get("Investigation Title"),
                "Invalid Investigation Title");

        // INVESTIGATION PUBLICATIONS
        assertEquals("17439666", metadata.get("Investigation PubMed ID"),
                "Invalid Investigation PubMed ID");
        assertEquals("doi:10.1186/jbiol54",
                metadata.get("Investigation Publication DOI"),
                "Invalid Investigation Publication DOI");

        // INVESTIGATION CONTACTS
        assertEquals( "Oliver", metadata.get("Investigation Person Last Name"),
                "Invalid Investigation Person Last Name");
        assertEquals("Stephen", metadata.get("Investigation Person First Name"),
                "Invalid Investigation Person First Name");
    }

    @Test
    public void testAssayPathTraversal(@TempDir Path root) throws Exception {
        Path secret = root.resolve("secret.txt");
        Files.write(secret, "SUPERSECRETTOKEN12345".getBytes(StandardCharsets.UTF_8));

        Path isaDir = Files.createDirectory(root.resolve("isa"));
        Files.write(isaDir.resolve("i_test.txt"),
                ("STUDY\n"
                        + "Study File Name\t\"s_test.txt\"\n"
                        + "Study Assay File Name\t\"../secret.txt\"\n")
                        .getBytes(StandardCharsets.UTF_8));
        Path study = isaDir.resolve("s_test.txt");
        Files.write(study, "\"Source Name\"\n\"culture1\"\n".getBytes(StandardCharsets.UTF_8));

        Parser parser = new ISArchiveParser(isaDir.toString());
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "s_test.txt");
        ParseContext context = new ParseContext();

        try (TikaInputStream tis = TikaInputStream.get(study)) {
            assertThrows(TikaException.class,
                    () -> parser.parse(tis, handler, metadata, context));
        }
        assertFalse(handler.toString().contains("SUPERSECRETTOKEN12345"),
                "assay reader escaped the ISA-Tab directory");
    }
}
