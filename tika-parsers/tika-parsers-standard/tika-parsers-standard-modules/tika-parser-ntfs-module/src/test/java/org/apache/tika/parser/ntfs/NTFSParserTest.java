package org.apache.tika.parser.ntfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


import java.io.InputStream;
import java.util.List;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;


public class NTFSParserTest {

    // This test currently uses a very simple dummy file "test-ntfs.img"
    // which only has the "NTFS" signature. SleuthKit will not be able to
    // fully parse this as a valid NTFS image.
    // A more comprehensive test would require a real, small NTFS image
    // containing known files and directories.

    @Test
    public void testParseNTFSDummyImage() throws Exception {
        Parser parser = new AutoDetectParser(TikaConfig.getDefaultConfig()); // Will pick up our parser via service loading
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = NTFSParserTest.class.getResourceAsStream("test-ntfs.img")) {
            assertNotNull(stream, "Test NTFS image not found");
            parser.parse(stream, handler, metadata, new ParseContext());
        } catch (Exception e) {
            // Given our dummy image is not a valid NTFS filesystem,
            // SleuthKit is expected to throw an exception (e.g., "No file systems found in image").
            // We want to ensure it's a TikaException wrapping a SleuthKit/TSK exception,
            // not an NPE or other unexpected runtime error from the parser itself.
            String errorMessage = e.getMessage();
            assertTrue(e instanceof org.apache.tika.exception.TikaException, "Exception should be a TikaException");
            assertTrue(errorMessage != null && errorMessage.contains("Sleuth Kit processing error"),
                    "Exception message should indicate a Sleuth Kit processing error. Actual: " + errorMessage);
             assertTrue(errorMessage.contains("No file systems found in the image") || errorMessage.contains("Error opening image"),
                    "Exception message should indicate no file systems found or error opening image. Actual: " + errorMessage);
        }

        // Even if parsing the content fails due to the dummy nature of the file,
        // the detector should have run and set the content type.
        assertEquals(MediaType.application("x-ntfs-image").toString(), metadata.get(Metadata.CONTENT_TYPE),
                "Content-Type should be detected as x-ntfs-image");
    }

    @Test
    @Disabled("Requires a real NTFS image and full Sleuth Kit setup")
    public void testParseRealNTFSImage() throws Exception {
        // This test would require a real NTFS image file (e.g., "sample.ntfs.img")
        // placed in the test resources.
        // For now, it's disabled.
        // To enable:
        // 1. Add a small, valid NTFS image to src/test/resources/org/apache/tika/parser/ntfs/
        // 2. Update the filename below.
        // 3. Add assertions for expected files, metadata, and content.

        /*
        Parser parser = new AutoDetectParser(TikaConfig.getDefaultConfig());
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream stream = NTFSParserTest.class.getResourceAsStream("sample.ntfs.img")) {
            assertNotNull(stream, "Test NTFS image 'sample.ntfs.img' not found");
            parser.parse(stream, handler, metadata, context);
        }

        // Example Assertions (highly dependent on the content of 'sample.ntfs.img'):
        assertEquals(MediaType.application("x-ntfs-image").toString(), metadata.get(Metadata.CONTENT_TYPE));

        // Check for specific files (if the image contains them)
        // This part depends on how EmbeddedDocumentExtractor and XHTMLContentHandler output them.
        // Typically, you might check for resources in metadata or specific XHTML tags.
        // For example, if there's a "file1.txt" with content "Hello Tika":
        // assertTrue(handler.toString().contains("file1.txt"));
        // assertTrue(handler.toString().contains("Hello Tika"));

        // Check for metadata of embedded items. This would require a custom handler
        // or inspecting what ParsingEmbeddedDocumentExtractor puts into parent metadata,
        // or using a RecursiveParserWrapper.

        // For example, using Tika facade to get structured content:
        List<Metadata> allMetadata = new Tika().parseToMetadata(
             NTFSParserTest.class.getResourceAsStream("sample.ntfs.img")
        );
        assertTrue(allMetadata.stream().anyMatch(m -> "file1.txt".equals(m.get(TikaCoreProperties.RESOURCE_NAME_KEY))));
        */
        fail("Test 'testParseRealNTFSImage' needs a real NTFS image and assertions to be implemented.");
    }
}
