package org.apache.tika.parser.ntfs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NTFSDetectorTest {

    private NTFSDetector detector;

    @BeforeEach
    public void setUp() {
        detector = new NTFSDetector();
    }

    @Test
    public void testDetectNTFS() throws Exception {
        // Load the dummy NTFS image created earlier
        // This stream represents a file starting with "AAA NTFS..."
        try (InputStream stream = getClass().getResourceAsStream("test-ntfs.img")) {
            Metadata metadata = new Metadata();
            MediaType mediaType = detector.detect(stream, metadata);
            assertEquals(MediaType.application("x-ntfs-image"), mediaType,
                    "Should detect NTFS image");
        }
    }

    @Test
    public void testDetectNonNTFS() throws Exception {
        // A simple text file, definitely not NTFS
        byte[] nonNtfsData = "This is a simple text file.".getBytes(StandardCharsets.UTF_8);
        try (InputStream stream = TikaInputStream.get(nonNtfsData)) {
            Metadata metadata = new Metadata();
            MediaType mediaType = detector.detect(stream, metadata);
            assertEquals(MediaType.OCTET_STREAM, mediaType,
                    "Should return OCTET_STREAM for non-NTFS file");
        }
    }

    @Test
    public void testDetectEmptyStream() throws Exception {
        try (InputStream stream = TikaInputStream.get(new byte[0])) {
            Metadata metadata = new Metadata();
            MediaType mediaType = detector.detect(stream, metadata);
            assertEquals(MediaType.OCTET_STREAM, mediaType,
                    "Should return OCTET_STREAM for an empty stream");
        }
    }

    @Test
    public void testDetectShortStream() throws Exception {
        // Stream shorter than the signature itself
        byte[] shortData = "NTF".getBytes(StandardCharsets.US_ASCII);
         try (InputStream stream = TikaInputStream.get(new byte[] {0x00, 0x00, 0x00, 'N', 'T', 'F'})) {
            Metadata metadata = new Metadata();
            MediaType mediaType = detector.detect(stream, metadata);
            assertEquals(MediaType.OCTET_STREAM, mediaType,
                    "Should return OCTET_STREAM for a stream too short to contain the full signature");
        }
    }

     @Test
    public void testDetectStreamWithDifferentSignature() throws Exception {
        byte[] diffSigData = "AAA NOTFS sig".getBytes(StandardCharsets.US_ASCII);
        try (InputStream stream = TikaInputStream.get(diffSigData)) {
            Metadata metadata = new Metadata();
            MediaType mediaType = detector.detect(stream, metadata);
            assertEquals(MediaType.OCTET_STREAM, mediaType,
                    "Should return OCTET_STREAM for a stream with a different signature");
        }
    }

    @Test
    public void testDetectNullStream() throws Exception {
        Metadata metadata = new Metadata();
        MediaType mediaType = detector.detect(null, metadata);
        assertEquals(MediaType.OCTET_STREAM, mediaType,
                "Should return OCTET_STREAM for a null stream");
    }
}
