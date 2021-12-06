package org.apache.tika.parser.microsoft.onenote;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.junit.jupiter.api.Test;

public class TestFssHttpb extends TikaTest {

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
        assertContains("R\u0000o\u0000b\u0000e\u0000r\u0000t\u0000 \u0000L\u0000u\u0000c\u0000a\u0000r\u0000i\u0000n\u0000i\u0000\u0000\u0000", mostRecentAuthors);

        assertEquals(Instant.ofEpochSecond(1591712300),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("creationTimestamp"))));
        assertEquals(Instant.ofEpochMilli(1623252330000L),
                Instant.ofEpochMilli(Long.parseLong(metadata.get("lastModifiedTimestamp"))));
        assertEquals(Instant.ofEpochSecond(1623597587),
                Instant.ofEpochSecond(Long.parseLong(metadata.get("lastModified"))));

        assertContains("Section1Page1Content", txt);
    }
}
