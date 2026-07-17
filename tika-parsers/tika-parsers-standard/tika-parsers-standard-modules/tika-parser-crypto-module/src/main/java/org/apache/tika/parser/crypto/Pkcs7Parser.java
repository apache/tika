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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Set;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cms.CMSCompressedDataParser;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.CMSTypedStream;
import org.bouncycastle.cms.jcajce.ZlibExpanderProvider;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for PKCS7/CMS data. Refines the content type to the CMS subtype (illustrator-style:
 * coarse magic routes here, this parser sets the {@code smime-type}) and extracts the embedded
 * content of a signed-data message. Non-signed or detached messages are labelled but not
 * extracted (their payload is absent, encrypted, or unsupported) and never throw.
 */
@TikaComponent
public class Pkcs7Parser implements Parser {

    private static final long serialVersionUID = -7310531559075115044L;

    private static final MediaType PKCS7_MIME = MediaType.application("pkcs7-mime");
    private static final MediaType PKCS7_SIGNATURE = MediaType.application("pkcs7-signature");
    private static final Set<MediaType> SUPPORTED_TYPES =
            MediaType.set(PKCS7_MIME, PKCS7_SIGNATURE);

    // cap the inflated output of a CMS compressedData so a zlib bomb (tiny compressed -> huge
    // output) can't blow up the parse; content beyond this is dropped
    private static final long MAX_DECOMPRESSED = 100L * 1024 * 1024;

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        tis.setCloseShield();
        try {
            // route on the cheap ContentInfo OID; signedData is then parsed exactly once (in
            // parseSignedData), which both labels the subtype and extracts the payload
            ASN1ObjectIdentifier oid = CmsClassifier.contentType(tis);
            if (PKCSObjectIdentifiers.signedData.equals(oid)) {
                parseSignedData(tis, handler, metadata, context);
            } else if (PKCSObjectIdentifiers.id_ct_compressedData.equals(oid)) {
                metadata.set(Metadata.CONTENT_TYPE, CmsClassifier.COMPRESSED.toString());
                extractCompressedContent(tis, handler, metadata, context);
            } else {
                // enveloped / digested / encrypted (labelled; payload absent, encrypted, or
                // unsupported) or unknown / non-CMS -> empty doc, no throw
                MediaType type = CmsClassifier.nonSignedType(oid);
                if (type != null) {
                    metadata.set(Metadata.CONTENT_TYPE, type.toString());
                }
                emptyDocument(handler, metadata);
            }
        } finally {
            tis.removeCloseShield();
        }
    }

    /** Parse a signedData message once: set the refined subtype and extract the embedded payload if present. */
    private void parseSignedData(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                                 ParseContext context)
            throws IOException, SAXException, TikaException {
        try (InputStream is = Files.newInputStream(tis.getPath())) {
            DigestCalculatorProvider digestCalculatorProvider =
                    new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
            CMSSignedDataParser parser = new CMSSignedDataParser(digestCalculatorProvider, is);
            try {
                CMSTypedStream content = parser.getSignedContent();
                metadata.set(Metadata.CONTENT_TYPE,
                        CmsClassifier.refinedSignedType(parser, content).toString());
                if (content != null) {
                    try (TikaInputStream contentTis = TikaInputStream.get(content.getContentStream())) {
                        Parser delegate = context.get(Parser.class, EmptyParser.INSTANCE);
                        delegate.parse(contentTis, handler, Metadata.newInstance(context), context);
                    }
                } else {
                    emptyDocument(handler, metadata);   // detached / certs-only: nothing to extract
                }
            } finally {
                parser.close();
            }
        } catch (OperatorCreationException e) {
            throw new TikaException("Unable to create DigestCalculatorProvider", e);
        } catch (CMSException e) {
            throw new TikaException("Unable to parse pkcs7 signed data", e);
        }
    }

    /** Inflate CMS compressedData (RFC 3274, zlib) and delegate-parse the inner payload. */
    private void extractCompressedContent(TikaInputStream tis, ContentHandler handler,
                                          Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        try (InputStream is = Files.newInputStream(tis.getPath())) {
            CMSCompressedDataParser parser = new CMSCompressedDataParser(is);
            CMSTypedStream content = parser.getContent(new ZlibExpanderProvider());
            BoundedInputStream inflated =
                    new BoundedInputStream(MAX_DECOMPRESSED, content.getContentStream());
            try (TikaInputStream contentTis = TikaInputStream.get(inflated)) {
                Parser delegate = context.get(Parser.class, EmptyParser.INSTANCE);
                delegate.parse(contentTis, handler, Metadata.newInstance(context), context);
            }
            if (inflated.hasHitBound()) {
                // zlib-bomb guard tripped: the payload was truncated at MAX_DECOMPRESSED, so record
                // a non-fatal warning rather than let a partial extraction look complete
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        "CMS compressedData payload exceeded " + MAX_DECOMPRESSED +
                                " bytes and was truncated");
            }
        } catch (CMSException e) {
            throw new TikaException("Unable to parse pkcs7 compressed data", e);
        }
    }

    private void emptyDocument(ContentHandler handler, Metadata metadata) throws SAXException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }

}
