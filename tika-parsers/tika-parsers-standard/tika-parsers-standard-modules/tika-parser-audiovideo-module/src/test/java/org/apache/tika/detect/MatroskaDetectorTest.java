package org.apache.tika.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.jupiter.api.Test;

public class MatroskaDetectorTest {

    private final MatroskaDetector detector = new MatroskaDetector();

    private InputStream getResourceAsStream(String resourcePath) {
        return this.getClass().getResourceAsStream(resourcePath);
    }

    @Test
    public void testDetectMKV() throws IOException {
        assertEquals(MediaType.video("x-matroska"),
                detector.detect(getResourceAsStream("/test-documents/sample.nonexist"),
                        new Metadata()));
    }
}