package org.apache.tika.detect;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Decoder;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class Pkcs7Detector implements Detector {
    private static final long serialVersionUID = 4651588855075311797L;
    private static byte[] PKCS = { 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01 };

    private byte[] decodePem(byte[] buf) {
        if (buf.length >= 21 && "-----BEGIN PKCS7-----".equals(new String(buf, 0, 21, StandardCharsets.US_ASCII))) {
            try {
                ByteArrayInputStream is = new ByteArrayInputStream(buf);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.US_ASCII));
                // consume first line
                reader.readLine();
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                Decoder decoder = Base64.getDecoder();
                String s;
                int len;
                while ((s = reader.readLine()) != null && s.charAt(0) != '-') {
                    len = (s.length() / 4) * 4;
                    os.write(decoder.decode(s.substring(0, len).getBytes(StandardCharsets.US_ASCII)));
                }
                return os.toByteArray();
            } catch (Exception e) {
            }
        }
        return buf;
    }

    public MediaType detect(byte[] input, Metadata metadata) {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }
        input = decodePem(input);
        int from = 0;
        int to = input.length;
        BerValue ber = BerValue.next(input, 0, to, false);
        if (ber != null && ber.isSequence()) {
            from += ber.getHeader();
            ber = BerValue.next(input, from, to, false);
            if (ber != null && ber.isOID()) {
                from += ber.getHeader();
                if (isPKCS(input, from, from  + PKCS.length)) {
                    from += PKCS.length;
                    int left = ber.getLength() - PKCS.length;
                    if (isCompressedData(input, from, to, left)) {
                        return MediaType.parse("application/pkcs7-mime; smime-type=compressed-data");
                    } else if (isEnvelopedData(input, from, to, left)) {
                        return MediaType.parse("application/pkcs7-mime; smime-type=enveloped-data");
                    } else if (isSignedData(input, from, to, left)) {
                        from += left;
                        // read all "digestAlgorithms"
                        ber = detectDigestAlgorithms(input, from, to);
                        if (ber != null) {
                            if (ber.getLength() == 0) {
                                // no digest algorithm means no data was signed
                                return MediaType.parse("application/pkcs7-mime; smime-type=certs-only");
                            }
                            from = ber.getOffset() + ber.getHeader() + ber.getLength() + (ber.isIndefinite() ? 2 : 0);
                            ber = BerValue.next(input, from, to, false);
                            if (ber != null && ber.isSequence()) {
                                from += ber.getHeader();
                                if (!ber.isIndefinite()) {
                                    // limit to "encapContentInfo" not to
                                    // mistake "certificates" for eContent (both
                                    // are context[0])
                                    to = Math.min(to, from + ber.getLength());
                                }
                                ber = BerValue.next(input, from, to, false);
                                if (ber != null && ber.isOID()) {
                                    from += ber.getHeader() + ber.getLength();
                                    ber = BerValue.next(input, from, to, false);
                                    if (ber == null || ber.isEOC()) {
                                        return MediaType.parse("application/pkcs7-signature");
                                    } else {
                                        return MediaType.parse("application/pkcs7-mime; smime-type=signed-data");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return MediaType.OCTET_STREAM;
    }

    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }
        byte[] buffer = new byte[512];
        int offset = 0, length;
        while ((length = input.read(buffer, offset, buffer.length - offset)) != -1 && offset < buffer.length) {
            offset += length;
        }
        return detect(Arrays.copyOf(buffer, offset), metadata);
    }

    private BerValue detectDigestAlgorithms(byte[] input, int from, int to) {
        BerValue ber = BerValue.next(input, from, to, false);
        if (ber != null && ber.isContext(0)) {
            from += ber.getHeader();
            ber = BerValue.next(input, from, to, false);
            if (ber != null && ber.isSequence()) {
                from += ber.getHeader();
                ber = BerValue.next(input, from, to, false);
                if (ber != null && ber.isInteger()) {
                    from += ber.getHeader() + ber.getLength();
                    ber = BerValue.next(input, from, to, true);
                    if (ber != null && ber.isSet()) {
                        return ber;
                    }
                }
            }
        }
        return null;
    }

    private boolean isCompressedData(byte[] input, int from, int to, int left) {
        if (left == 4 && input.length > from + 3 && input[from] == 0x09 && input[from + 1] == 0x10
                && input[from + 2] == 0x01 && input[from + 3] == 0x09) {
            return true;
        }
        return false;
    }

    private boolean isEnvelopedData(byte[] input, int from, int to, int left) {
        if (left == 2 && input.length > from + 1 && input[from] == 0x07 && input[from + 1] == 0x03) {
            return true;
        }
        return false;
    }

    private boolean isPKCS(byte[] input, int from, int to) {
        if (input.length < to) {
            return false;
        }
        for (int i = from; i < to; i++) {
            if (input[i] != PKCS[i - from]) {
                return false;
            }
        }
        return true;
    }

    private boolean isSignedData(byte[] input, int from, int to, int left) {
        if (left == 2 && input.length > from + 1 && input[from] == 0x07 && input[from + 1] == 0x02) {
            return true;
        }
        return false;
    }
}
