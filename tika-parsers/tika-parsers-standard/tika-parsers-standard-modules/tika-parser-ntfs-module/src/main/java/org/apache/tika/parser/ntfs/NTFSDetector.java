package org.apache.tika.parser.ntfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class NTFSDetector implements Detector {

    private static final long serialVersionUID = 1L;

    // NTFS signature is "NTFS" at offset 0x03
    private static final int SIGNATURE_OFFSET = 3;
    private static final byte[] NTFS_SIGNATURE = "NTFS".getBytes(StandardCharsets.US_ASCII);
    // The buffer size should be enough to read the signature
    private static final int BUFFER_SIZE = SIGNATURE_OFFSET + NTFS_SIGNATURE.length;


    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }

        TikaInputStream tis = TikaInputStream.cast(input);
        if (tis != null) {
            tis.mark(BUFFER_SIZE);
            try {
                return detectFromStream(tis);
            } finally {
                tis.reset();
            }
        } else {
            // If it's not a TikaInputStream, we can't mark/reset.
            // For robust detection, we might need to read into a temporary buffer
            // or decide if detection is possible without mark/reset.
            // For now, let's assume Tika provides a mark-supported stream.
            // If not, this path might consume the stream prefix.
            // A simple detector might not be able to work with a non-mark/reset stream
            // without consuming its beginning.
            // Consider logging a warning or throwing an exception if robust detection
            // without mark/reset is critical.
            // For this initial implementation, we'll proceed, but this is a known limitation.
            return detectFromStream(input);
        }
    }

    private MediaType detectFromStream(InputStream stream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead = stream.read(buffer, 0, BUFFER_SIZE);

        if (bytesRead < BUFFER_SIZE) {
            // Not enough bytes to check the signature
            return MediaType.OCTET_STREAM;
        }

        byte[] signatureInStream = Arrays.copyOfRange(buffer, SIGNATURE_OFFSET, BUFFER_SIZE);

        if (Arrays.equals(NTFS_SIGNATURE, signatureInStream)) {
            return MediaType.application("x-ntfs-image");
        }

        return MediaType.OCTET_STREAM;
    }
}
