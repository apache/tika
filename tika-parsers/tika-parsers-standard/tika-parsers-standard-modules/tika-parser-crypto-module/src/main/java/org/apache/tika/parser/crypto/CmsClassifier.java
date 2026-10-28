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
package org.apache.tika.parser.crypto;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1SequenceParser;
import org.bouncycastle.asn1.ASN1StreamParser;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.CMSTypedStream;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

/**
 * Classifies a CMS/PKCS7 stream by its {@code ContentInfo} content-type OID (and, for signedData,
 * its sub-shape) into a refined Tika {@link MediaType}. Shared by {@link Pkcs7Parser} (parse-time
 * labelling) and the optional detector. Reads via the spooled file and returns {@code null} if the
 * stream is not recognizable CMS, so a caller can fall back to the coarse type.
 */
public final class CmsClassifier {

    // cap the first primitive read when peeking the content-type OID (an OID is a few bytes)
    private static final int OID_PEEK_LIMIT = 1 << 16;

    private static final byte[][] PEM_PREFIXES = {
            "-----BEGIN PKCS7".getBytes(StandardCharsets.US_ASCII),
            "-----BEGIN CMS".getBytes(StandardCharsets.US_ASCII)};

    static final MediaType PKCS7_MIME = MediaType.application("pkcs7-mime");
    static final MediaType PKCS7_SIGNATURE = MediaType.application("pkcs7-signature");
    static final MediaType SIGNED = smime("signed-data");
    static final MediaType CERTS_ONLY = smime("certs-only");
    static final MediaType ENVELOPED = smime("enveloped-data");
    static final MediaType COMPRESSED = smime("compressed-data");
    static final MediaType DIGESTED = smime("digested-data");
    static final MediaType ENCRYPTED = smime("encrypted-data");

    private CmsClassifier() {
    }

    private static MediaType smime(String smimeType) {
        return new MediaType("application", "pkcs7-mime", Map.of("smime-type", smimeType));
    }

    /**
     * @return the refined media type, or {@code null} if the stream is not recognizable CMS.
     * @throws IOException on a genuine spool/read failure — distinct from "not CMS" (which returns
     *         {@code null}), so a real I/O error is not silently masked as a non-crypto stream.
     */
    public static MediaType classify(TikaInputStream tis) throws IOException {
        try (TikaInputStream der = decodePem(tis)) {
            return classifyDer(der != null ? der : tis);
        }
    }

    private static MediaType classifyDer(TikaInputStream tis) throws IOException {
        ASN1ObjectIdentifier oid = contentType(tis);
        if (oid == null) {
            return null;
        }
        MediaType nonSigned = nonSignedType(oid);
        if (nonSigned != null) {
            return nonSigned;
        }
        if (PKCSObjectIdentifiers.signedData.equals(oid)) {
            return refineSigned(tis.getPath());   // already spooled by contentType(tis)
        }
        return null;   // plain data or a non-CMS OID: let the coarse type stand
    }

    /**
     * The {@code ContentInfo} content-type OID, or {@code null} if this is not a DER {@code SEQUENCE}
     * / not recognizable CMS. Peeks the leading byte before spooling, so a non-DER stream (the
     * overwhelmingly common non-crypto case) is rejected without writing a temp file.
     *
     * @throws IOException on a genuine spool/read failure.
     */
    static ASN1ObjectIdentifier contentType(TikaInputStream tis) throws IOException {
        if (tis == null || !looksLikeDerSequence(tis)) {
            return null;
        }
        Path path = tis.getPath();   // spool so we can read it more than once; IOException propagates
        return path == null ? null : contentType(path);
    }

    /** Map a non-signedData ContentInfo OID to its refined type; {@code null} for signedData/plain/unknown. */
    static MediaType nonSignedType(ASN1ObjectIdentifier oid) {
        if (PKCSObjectIdentifiers.envelopedData.equals(oid)) {
            return ENVELOPED;
        } else if (PKCSObjectIdentifiers.id_ct_compressedData.equals(oid)) {
            return COMPRESSED;
        } else if (PKCSObjectIdentifiers.digestedData.equals(oid)) {
            return DIGESTED;
        } else if (PKCSObjectIdentifiers.encryptedData.equals(oid)) {
            return ENCRYPTED;
        }
        return null;
    }

    /**
     * A PEM-armored ({@code -----BEGIN PKCS7} / {@code -----BEGIN CMS}) stream decoded to DER, or
     * {@code null} if the stream is not PEM CMS or its body does not decode. The caller closes the
     * result; {@code tis} itself is spooled, not consumed.
     *
     * @throws IOException on a genuine spool failure of {@code tis}.
     */
    static TikaInputStream decodePem(TikaInputStream tis) throws IOException {
        if (tis == null || !startsWithPemPrefix(tis)) {
            return null;
        }
        Path path = tis.getPath();
        if (path == null) {
            return null;
        }
        TikaInputStream der = TikaInputStream.get(
                Base64.getMimeDecoder().wrap(new PemBody(new BufferedInputStream(Files.newInputStream(path)))));
        try {
            der.getPath();   // decode eagerly so malformed base64 reads as "not CMS", not an I/O error
            return der;
        } catch (IOException | IllegalArgumentException e) {
            der.close();
            return null;
        }
    }

    private static boolean startsWithPemPrefix(TikaInputStream tis) throws IOException {
        byte[] head = new byte[PEM_PREFIXES[0].length];
        int n = tis.peek(head);
        for (byte[] prefix : PEM_PREFIXES) {
            if (n >= prefix.length && Arrays.equals(head, 0, prefix.length, prefix, 0, prefix.length)) {
                return true;
            }
        }
        return false;
    }

    /** Cheap DER-{@code SEQUENCE} gate: peek one byte (mark/reset, no spool). */
    private static boolean looksLikeDerSequence(TikaInputStream tis) throws IOException {
        byte[] head = new byte[1];
        return tis.peek(head) == 1 && (head[0] & 0xFF) == 0x30;
    }

    /** Lazily read only the outer {@code SEQUENCE}'s first element (the ContentInfo OID). */
    private static ASN1ObjectIdentifier contentType(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            ASN1StreamParser sp = new ASN1StreamParser(is, OID_PEEK_LIMIT);
            ASN1Encodable top = sp.readObject();
            if (!(top instanceof ASN1SequenceParser)) {
                return null;
            }
            ASN1Encodable first = ((ASN1SequenceParser) top).readObject();
            if (first == null) {
                return null;
            }
            ASN1Primitive prim = first.toASN1Primitive();
            return prim instanceof ASN1ObjectIdentifier ? (ASN1ObjectIdentifier) prim : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Distinguish a signedData's sub-shape via BouncyCastle's streaming parser: content present ->
     * signed-data (no drain); otherwise certs + no signers -> certs-only, else a detached signature.
     */
    private static MediaType refineSigned(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            DigestCalculatorProvider dcp =
                    new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
            CMSSignedDataParser parser = new CMSSignedDataParser(dcp, is);
            try {
                return refinedSignedType(parser, parser.getSignedContent());
            } finally {
                parser.close();
            }
        } catch (Exception e) {
            return PKCS7_MIME;   // it is signedData but we could not refine the sub-shape
        }
    }

    /**
     * Sub-shape of an open signedData parser, given its already-fetched signed content: embedded
     * payload -&gt; signed-data (no drain needed); otherwise certs and no signers -&gt; certs-only,
     * else a detached signature. Shared by {@link #refineSigned(Path)} (detector labelling) and
     * {@code Pkcs7Parser}, which reuses the same parser to also extract — avoiding a second parse.
     */
    static MediaType refinedSignedType(CMSSignedDataParser parser, CMSTypedStream content)
            throws CMSException {
        if (content != null) {
            return SIGNED;
        }
        boolean hasSigners = !parser.getSignerInfos().getSigners().isEmpty();
        boolean hasCerts = !parser.getCertificates().getMatches(null).isEmpty();
        return (hasCerts && !hasSigners) ? CERTS_ONLY : PKCS7_SIGNATURE;
    }

    /** The base64 body of a PEM block: skips the BEGIN line, ends at the {@code -----END} line. */
    private static final class PemBody extends FilterInputStream {

        private boolean inBody;
        private boolean done;

        PemBody(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            if (done) {
                return -1;
            }
            int c;
            while (!inBody) {
                c = in.read();
                if (c == -1) {
                    done = true;
                    return -1;
                }
                inBody = c == '\n';
            }
            c = in.read();
            // '-' is outside the base64 alphabet, so it can only start the END line
            if (c == -1 || c == '-') {
                done = true;
                return -1;
            }
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int i = 0;
            for (; i < len; i++) {
                int c = read();
                if (c == -1) {
                    break;
                }
                b[off + i] = (byte) c;
            }
            return i == 0 && len > 0 ? -1 : i;
        }

        @Override
        public long skip(long n) throws IOException {
            long i = 0;
            while (i < n && read() != -1) {
                i++;
            }
            return i;
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public boolean markSupported() {
            return false;
        }
    }
}
