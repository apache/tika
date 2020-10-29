package org.apache.tika.detect;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class FileCommandDetectorTest {

    private static Detector DETECTOR;

    @BeforeClass
    public static void setUp() throws Exception {
        try (InputStream is = TikaConfig.class.getResourceAsStream("FileCommandDetector.xml")) {
            DETECTOR = new TikaConfig(is).getDetector();
        }
    }

    @Test
    public void testBasic() throws Exception {
        assumeTrue(FileCommandDetector.checkHasFile());

        try (InputStream is = getClass().getResourceAsStream("/test-documents/basic_embedded.xml")) {
            assertEquals(MediaType.text("xml"), DETECTOR.detect(is, new Metadata()));
            //make sure that the detector is resetting the stream
            assertEquals(MediaType.text("xml"), DETECTOR.detect(is, new Metadata()));
        }

        //now try with TikaInputStream
        try (InputStream is = TikaInputStream.get(getClass()
                .getResourceAsStream("/test-documents/basic_embedded.xml"))) {
            assertEquals(MediaType.text("xml"), DETECTOR.detect(is, new Metadata()));
            //make sure that the detector is resetting the stream
            assertEquals(MediaType.text("xml"), DETECTOR.detect(is, new Metadata()));
        }
    }
}
