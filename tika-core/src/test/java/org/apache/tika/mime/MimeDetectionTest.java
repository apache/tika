package org.apache.tika.mime;

import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;

public class MimeDetectionTest extends TestCase {

    private MimeTypes mimeTypes;

    /** @inheritDoc */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.mimeTypes = TikaConfig.getDefaultConfig().getMimeRepository();
        //this.mimeTypes = MimeTypesFactory.create("/org/apache/tika/mime/tika-mimetypes-minimal.xml");
    }

    public void testDetection() throws Exception {
        testFile("image/svg+xml", "circles.svg");
        testFile("image/svg+xml", "circles-with-prefix.svg");
        testFile("image/png", "datamatrix.png");
        testFile("text/html", "test.html");
        testFile("application/xml", "test-iso-8859-1.xml");
        testFile("application/xml", "test-utf8.xml");
        testFile("application/xml", "test-utf16le.xml");
        testFile("application/xml", "test-utf16be.xml");
        testFile("application/xml", "test-long-comment.xml");
        testFile("application/xslt+xml", "stylesheet.xsl");
    }

    private void testFile(String expected, String filename) throws IOException {
        InputStream in = getClass().getResourceAsStream(filename);
        assertNotNull("Test file not found: " + filename, in);
        if (!in.markSupported()) {
            in = new java.io.BufferedInputStream(in);
        }
        try {
            Metadata metadata = new Metadata();
            String mime = this.mimeTypes.detect(in, metadata).toString();
            assertEquals(filename + " is not properly detected.", expected, mime);

            //Add resource name and test again
            metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
            mime = this.mimeTypes.detect(in, metadata).toString();
            assertEquals(filename + " is not properly detected.", expected, mime);
        } finally {
            in.close();
        }
    }

}
