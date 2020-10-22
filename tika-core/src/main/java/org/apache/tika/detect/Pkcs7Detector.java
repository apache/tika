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
package org.apache.tika.detect;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Decoder;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class Pkcs7Detector implements Detector {
    private static final long serialVersionUID = 4651588855075311797L;

    public static MediaType PKCS7_MIME = MediaType.application("pkcs7-mime");
    public static MediaType PKCS7_SIGNATURE = MediaType.application("pkcs7-signature");
    private static MediaType COMPRESSED_DATA = new MediaType(PKCS7_MIME, "smime-type", "compressed-data");
    private static MediaType ENVELOPED_DATA = new MediaType(PKCS7_MIME, "smime-type", "enveloped-data");
    private static MediaType CERTS_ONLY = new MediaType(PKCS7_MIME, "smime-type", "certs-only");
    private static MediaType SIGNED_DATA = new MediaType(PKCS7_MIME, "smime-type", "signed-data");

    private static byte[] PKCS = { 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01 };
    private static char[] BOUNDARY = "-----BEGIN PKCS7-----".toCharArray();

    private byte[] decodePem(byte[] buf, int from, int to) {
        try {
            ByteArrayInputStream is = new ByteArrayInputStream(buf);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.US_ASCII));
            // consume the encapsulation boundary
            reader.readLine();
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Decoder decoder = Base64.getDecoder();
            String s;
            while ((s = reader.readLine()) != null && s.charAt(0) != '-') {
                if ((s.length() & 0x03) != 0) {
                    // round to the closest multiple of 4
                    os.write(decoder.decode(s.substring(0, ((s.length() >> 2) << 2)).getBytes(StandardCharsets.US_ASCII)));
                } else {
                    os.write(decoder.decode(s.getBytes(StandardCharsets.US_ASCII)));
                }
            }
            return os.toByteArray();
        } catch (Exception e) {
            return buf;
        }
    }

    private MediaType detect(byte[] input, int from, int to, Metadata metadata) {
        if (isPem(input, from, to)) {
            input = decodePem(input, from, to);
            from = 0;
            to = input.length;
        }
        BerValue ber = BerValue.next(input, from, to, false);
        if (ber != null && ber.isSequence()) {
            from += ber.getHeader();
            ber = BerValue.next(input, from, to, false);
            if (ber != null && ber.isOID()) {
                from += ber.getHeader();
                if (isPKCS(input, from, from + PKCS.length)) {
                    from += PKCS.length;
                    int left = ber.getLength() - PKCS.length;
                    if (isCompressedData(input, from, to, left)) {
                        return COMPRESSED_DATA;
                    } else if (isEnvelopedData(input, from, to, left)) {
                        return ENVELOPED_DATA;
                    } else if (isSignedData(input, from, to, left)) {
                        from += left;
                        // read all "digestAlgorithms"
                        ber = detectDigestAlgorithms(input, from, to);
                        if (ber != null) {
                            if (ber.getLength() == 0) {
                                // no digest algorithm means no data was signed
                                return CERTS_ONLY;
                            }
                            from = ber.getOffset() + ber.getHeader() + ber.getLength() + (ber.isIndefinite() ? 2 : 0);
                            ber = BerValue.next(input, from, to, false);
                            if (ber != null && ber.isSequence()) {
                                from += ber.getHeader();
                                if (!ber.isIndefinite()) {
                                    // limit to "encapContentInfo" not to
                                    // mistake "certificates" for "eContent"
                                    // (both are context[0])
                                    to = Math.min(to, from + ber.getLength());
                                }
                                ber = BerValue.next(input, from, to, false);
                                if (ber != null && ber.isOID()) {
                                    from += ber.getHeader() + ber.getLength();
                                    ber = BerValue.next(input, from, to, false);
                                    if (ber == null || ber.isEOC()) {
                                        return PKCS7_SIGNATURE;
                                    } else {
                                        return SIGNED_DATA;
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

    public MediaType detect(byte[] input, Metadata metadata) {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }
        return detect(input, 0, input.length, metadata);
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
        return detect(buffer, 0, offset, metadata);
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

    private boolean isPem(byte[] buf, int from, int to) {
        if (to >= from + BOUNDARY.length && buf.length >= to) {
            for (int i = 0; i < BOUNDARY.length; i++) {
                if (buf[from + i] != BOUNDARY[i]) {
                    return false;
                }
            }
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
