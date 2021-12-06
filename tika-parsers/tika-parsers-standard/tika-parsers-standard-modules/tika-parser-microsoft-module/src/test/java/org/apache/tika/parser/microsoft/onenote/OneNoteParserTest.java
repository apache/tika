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
package org.apache.tika.parser.microsoft.onenote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;

public class OneNoteParserTest extends TikaTest {

    //test recursive parser wrapper for image files

    /**
     * This is the sample document that is automatically created from onenote 2013.
     */
    @Test
    public void testOneNote2013Doc1() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote1.one", metadata);
        assertNoJunk(txt);

        List<String> authors = Arrays.asList(metadata.getValues("authors"));
        assertContains("Olya Veselova\u0000", authors);
        assertContains("Microsoft\u0000", authors);
        assertContains("Scott\u0000", authors);
        assertContains("Scott H. W. Snyder\u0000", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues("mostRecentAuthors"));
        assertContains("Microsoft\u0000", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues("originalAuthors"));
        assertContains("Microsoft\u0000", originalAuthors);

        assertEquals(Instant.ofEpochSecond(1336059427),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1383613114000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get("lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1446572147),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("lastModified"))));
    }

    @Test
    public void testOneNote2013Doc2() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote2.one", metadata);
        assertContains("wow this is neat", txt);
        assertContains("neat info about totally killin it bro", txt);
        assertContains("Section1TextArea1", txt);
        assertContains("Section1HeaderTitle", txt);
        assertContains("Section1TextArea2", txt);
        assertNoJunk(txt);

        List<String> authors = Arrays.asList(metadata.getValues("authors"));
        assertContains("Olya Veselova\u0000", authors);
        assertContains("Microsoft\u0000", authors);
        assertContains("Scott\u0000", authors);
        assertContains("Scott H. W. Snyder\u0000", authors);
        assertContains("ndipiazza\u0000", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues("mostRecentAuthors"));
        assertContains("ndipiazza\u0000", mostRecentAuthors);
        assertContains("Microsoft\u0000", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues("originalAuthors"));
        assertContains("Microsoft\u0000", originalAuthors);
        assertContains("ndipiazza\u0000", mostRecentAuthors);

        assertEquals(Instant.ofEpochSecond(1336059427),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1574426629000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get("lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1574426628),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("lastModified"))));
    }

    @Test
    public void testOneNote2013Doc3() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote3.one", metadata);
        assertContains("awesome information about sports or some crap like that.", txt);
        assertContains("Quit doing horrible things to me. Dang you. ", txt);
        assertContains("Section2TextArea1", txt);
        assertContains("Section2HeaderTitle", txt);
        assertContains("Section2TextArea2", txt);
        assertNoJunk(txt);

        List<String> authors = Arrays.asList(metadata.getValues("authors"));
        assertNotContained("Olya Veselova\u0000", authors);
        assertNotContained("Microsoft\u0000", authors);
        assertNotContained("Scott\u0000", authors);
        assertNotContained("Scott H. W. Snyder\u0000", authors);
        assertContains("ndipiazza\u0000", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues("mostRecentAuthors"));
        assertContains("ndipiazza\u0000", mostRecentAuthors);
        assertNotContained("Microsoft\u0000", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues("originalAuthors"));
        assertNotContained("Microsoft\u0000", originalAuthors);
        assertContains("ndipiazza\u0000", mostRecentAuthors);

        assertEquals(Instant.ofEpochSecond(1574426349),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1574426623000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get("lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1574426624),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("lastModified"))));
    }

    @Test
    public void testOneNote2013Doc4() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote4.one", metadata);

        assertContains("way too much information about poptarts to handle.", txt);
        assertContains("Section3TextArea1", txt);
        assertContains("Section3HeaderTitle", txt);
        assertContains("Section3TextArea2", txt);
        assertNoJunk(txt);

        List<String> authors = Arrays.asList(metadata.getValues("authors"));
        assertNotContained("Olya Veselova\u0000", authors);
        assertNotContained("Microsoft\u0000", authors);
        assertNotContained("Scott\u0000", authors);
        assertNotContained("Scott H. W. Snyder\u0000", authors);
        assertContains("ndipiazza\u0000", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues("mostRecentAuthors"));
        assertContains("ndipiazza\u0000", mostRecentAuthors);
        assertNotContained("Microsoft\u0000", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues("originalAuthors"));
        assertNotContained("Microsoft\u0000", originalAuthors);
        assertContains("ndipiazza\u0000", mostRecentAuthors);

        assertEquals(Instant.ofEpochSecond(1574426385),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1574426548000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get("lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1574426547),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("lastModified"))));
    }

    @Test
    public void testOneNote2016() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote2016.one", metadata);

        assertContains("So good", txt);
        assertContains("This is one note 2016", txt);
        assertNoJunk(txt);

        List<String> authors = Arrays.asList(metadata.getValues("authors"));
        assertContains("nicholas dipiazza\u0000", authors);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues("mostRecentAuthors"));
        assertContains("nicholas dipiazza\u0000", mostRecentAuthors);

        List<String> originalAuthors = Arrays.asList(metadata.getValues("originalAuthors"));
        assertContains("nicholas dipiazza\u0000", originalAuthors);

        assertEquals(Instant.ofEpochSecond(1576107472),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1576107481000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get("lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1576107480),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("lastModified"))));
    }

    @Test
    public void testOneNote2007OrEarlier() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNote2007OrEarlier.one", metadata);

        // utf-16 LE text
        assertContains(
                "One note is the application.  The notebooks are the files within the application" +
                        ".  " +
                        "Each notebook can have an unlimited amount of sections and pages.  To " +
                        "create a new notebook, go to file, new, computer, " +
                        "and name it.  It will go to my documents, oneNote Notebooks folder.  The" +
                        " notebook doesn't close and you don't have to save.  " +
                        "If it closes, you can go back to it and it will open at the same place " +
                        "you left off.  If you are offline and the notebook is " +
                        "being stored on a sharepoint site, you can work on it and it will sync " +
                        "when you go back online.", txt);
        // ascii text
        assertContains("Correlation between Outlook and OneNote", txt);
    }

    @Test
    public void testOneNoteEmbeddedWordDoc() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testOneNoteEmbeddedWordDoc.one");

        assertTrue(metadataList.stream().anyMatch(
                ml -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(
                        ml.get("Content-Type"))));
    }

    /**
     * Test a document pulled from Office 365 which stores the MS-ONESTORE document using the MS-FSSHTTPB
     * protocol.
     */
    @Test
    public void testOneNoteDocumentFromOffice365_1() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNoteFromOffice365.one", metadata);

        assertEquals(1, metadata.getValues("mostRecentAuthors").length);

        assertEquals(Instant.ofEpochSecond(1636621406),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1636621448000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get("lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1636621448),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("lastModified"))));
        assertContains("Section1Page1Content", txt);
    }

    /**
     * Test a document pulled from Office 365 which stores the MS-ONESTORE document using the MS-FSSHTTPB
     * protocol.
     */
    @Test
    public void testOneNoteDocumentFromOffice365_2() throws Exception {
        Metadata metadata = new Metadata();
        String txt = getText("testOneNoteFromOffice365-2.one", metadata);

        List<String> mostRecentAuthors = Arrays.asList(metadata.getValues("mostRecentAuthors"));
        assertContains(
                "R\u0000o\u0000b\u0000e\u0000r\u0000t\u0000 \u0000L\u0000u\u0000c\u0000a" +
                        "\u0000r\u0000i\u0000n\u0000i\u0000\u0000\u0000",
                mostRecentAuthors);

        assertEquals(Instant.ofEpochSecond(1591712300),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1623252330000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get("lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1623597587),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("lastModified"))));

        assertContains("Section1Page1Content", txt);
    }

    private void assertNoJunk(String txt) {
        //Should not include font names in the text
        assertNotContained("Calibri", txt);
        //Should not include UTF-16 property values that are garbage
        assertNotContained("\u5902", txt);
        assertNotContained("\u83F2", txt);
        assertNotContained("\u432F", txt);
        assertNotContained("\u01E1", txt);
    }
}
