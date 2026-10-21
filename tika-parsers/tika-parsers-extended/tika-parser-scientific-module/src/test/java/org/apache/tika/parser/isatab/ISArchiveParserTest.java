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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

public class ISArchiveParserTest extends TikaTest {

    @Test
    public void testParseArchive() throws Exception {
        // the parser reads the investigation file next to the study, so open the file itself
        Path study = Paths.get(ISArchiveParserTest.class
                .getResource("/test-documents/testISATab_BII-I-1/s_BII-S-1.txt").toURI());
        Parser parser = new ISArchiveParser();

        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "s_BII-S-1.txt");
        ParseContext context = new ParseContext();
        try (TikaInputStream tis = TikaInputStream.get(study)) {
            parser.parse(tis, handler, metadata, context);
        }

        // INVESTIGATION -- ISA-Tab field names are doc-derived, so ISATabUtils routes them
        // through the isatab: KeyPrefix (TIKA-4816).
        assertEquals("BII-I-1", metadata.get("isatab:Investigation Identifier"),
                "Invalid Investigation Identifier");
        assertEquals("Growth control of the eukaryote cell: a systems biology study in yeast",
                metadata.get("isatab:Investigation Title"),
                "Invalid Investigation Title");

        // INVESTIGATION PUBLICATIONS
        assertEquals("17439666", metadata.get("isatab:Investigation PubMed ID"),
                "Invalid Investigation PubMed ID");
        assertEquals("doi:10.1186/jbiol54",
                metadata.get("isatab:Investigation Publication DOI"),
                "Invalid Investigation Publication DOI");

        // INVESTIGATION CONTACTS
        assertEquals( "Oliver", metadata.get("isatab:Investigation Person Last Name"),
                "Invalid Investigation Person Last Name");
        assertEquals("Stephen", metadata.get("isatab:Investigation Person First Name"),
                "Invalid Investigation Person First Name");
    }

    @Test
    public void testAssayFileNameResolvedWithinDirectory(@TempDir Path root) throws Exception {
        // A file sitting next to (but outside of) the ISA-Tab directory.
        Path outside = root.resolve("outside.txt");
        Files.write(outside, "OUTSIDE_DIRECTORY_CONTENT".getBytes(StandardCharsets.UTF_8));

        // An ISA-Tab directory whose investigation file points an assay at the sibling file
        // using a relative name.
        Path isaDir = Files.createDirectory(root.resolve("isa"));
        Files.write(isaDir.resolve("i_test.txt"),
                ("STUDY\n"
                        + "Study File Name\t\"s_test.txt\"\n"
                        + "Study Assay File Name\t\"../outside.txt\"\n")
                        .getBytes(StandardCharsets.UTF_8));
        Path study = isaDir.resolve("s_test.txt");
        Files.write(study, "\"Source Name\"\n\"culture1\"\n".getBytes(StandardCharsets.UTF_8));

        Parser parser = new ISArchiveParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "s_test.txt");
        ParseContext context = new ParseContext();

        try (TikaInputStream tis = TikaInputStream.get(study)) {
            assertThrows(IOException.class,
                    () -> parser.parse(tis, handler, metadata, context));
        }
        assertFalse(handler.toString().contains("OUTSIDE_DIRECTORY_CONTENT"),
                "assay reader read a file outside the ISA-Tab directory");
    }

    @Test
    public void testDirectoryNotCachedAcrossParses(@TempDir Path root) throws Exception {
        Parser parser = new ISArchiveParser();
        assertEquals("FIRST", parseInvestigationId(parser, writeArchive(root, "first", "FIRST")));
        assertEquals("SECOND", parseInvestigationId(parser, writeArchive(root, "second", "SECOND")));
    }

    private static Path writeArchive(Path root, String dirName, String investigationId)
            throws IOException {
        Path isaDir = Files.createDirectory(root.resolve(dirName));
        Files.write(isaDir.resolve("i_test.txt"),
                ("INVESTIGATION\n"
                        + "Investigation Identifier\t\"" + investigationId + "\"\n"
                        + "STUDY\n"
                        + "Study File Name\t\"s_test.txt\"\n")
                        .getBytes(StandardCharsets.UTF_8));
        Path study = isaDir.resolve("s_test.txt");
        Files.write(study, "\"Source Name\"\n\"culture1\"\n".getBytes(StandardCharsets.UTF_8));
        return study;
    }

    private static String parseInvestigationId(Parser parser, Path study) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "s_test.txt");
        try (TikaInputStream tis = TikaInputStream.get(study)) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }
        return metadata.get("isatab:Investigation Identifier");
    }
}
