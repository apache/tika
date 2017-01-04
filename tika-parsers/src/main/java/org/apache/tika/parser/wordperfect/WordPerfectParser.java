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
package org.apache.tika.parser.wordperfect;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.UnsupportedFormatException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.WordPerfect;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * <p>Parser for Corel WordPerfect documents. Targets WP6 File Format
 * but appears to be compatible with more recent versions too.</p>
 * @author Pascal Essiembre 
 */
public class WordPerfectParser extends AbstractParser {

    private static final long serialVersionUID = 8941810225917012232L;

    final static MediaType WP_BASE = MediaType.application("vnd.wordperfect");

    final static MediaType WP_UNK =
            new MediaType(WP_BASE, "version", "unknown");

    final static MediaType WP_5_0 = new MediaType(WP_BASE, "version", "5.0");

    final static MediaType WP_5_1 = new MediaType(WP_BASE, "version", "5.1");

    final static MediaType WP_6_x = new MediaType(WP_BASE, "version", "6.x");

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    WP_5_0, WP_5_1, WP_6_x)));
    
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, 
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        
        WPInputStream wpStream = new WPInputStream(stream);
        WPPrefixArea prefixArea = WPPrefixAreaExtractor.extract(wpStream);
        
        ensureFileSupport(prefixArea, metadata);
        
        applyMetadata(prefixArea, metadata);
        
        extractDocumentArea(prefixArea, wpStream, 
                new XHTMLContentHandler(handler, metadata));
    }
    
    private void extractDocumentArea(WPPrefixArea prefixArea,
            WPInputStream in, XHTMLContentHandler xhtml)
                    throws SAXException, IOException {

        // Move to offset (for some reason skip() did not work).
        for (int i = 0; i < prefixArea.getDocAreaPointer(); i++) {
            in.readWPByte();
        }
        
        xhtml.startDocument();

        getDocumentAreaExtractor(prefixArea).extract(in, xhtml);
        
        xhtml.endDocument();
    }
    
    private void ensureFileSupport(WPPrefixArea pa, Metadata metadata)
            throws UnsupportedFormatException, EncryptedDocumentException {
        if (pa.getMajorVersion() != WPPrefixArea.WP5_MAJOR_VERSION
                && pa.getMajorVersion() != WPPrefixArea.WP6_MAJOR_VERSION) {
            metadata.set(Metadata.CONTENT_TYPE, WP_UNK.toString());
            throw new UnsupportedFormatException(
                    "Parser doesn't recognize this major version: "
                            + pa.getMajorVersion());
        }
        if (pa.isEncrypted()) {
            throw new EncryptedDocumentException();
        }
    }
    
    private void applyMetadata(WPPrefixArea pa, Metadata metadata) {
        // Should we force the more precise media type if only the base
        // form is found?  Or shall we store a friendly representation
        // of the version in a new field?
        if (metadata.get(Metadata.CONTENT_TYPE) == null) {
            if (pa.getMajorVersion() == WPPrefixArea.WP6_MAJOR_VERSION) {
                metadata.set(Metadata.CONTENT_TYPE, WP_6_x.toString());
            } else if (pa.getMinorVersion() == 
                    WPPrefixArea.WP5_0_MINOR_VERSION) {
                metadata.set(Metadata.CONTENT_TYPE, WP_5_0.toString());
            } else if (pa.getMinorVersion() == 
                    WPPrefixArea.WP5_1_MINOR_VERSION) {
                metadata.set(Metadata.CONTENT_TYPE, WP_5_1.toString());
            } else {
                metadata.set(Metadata.CONTENT_TYPE, WP_BASE.toString());
            }
        }
        
        metadata.set(WordPerfect.FILE_ID, pa.getFileId());
        metadata.set(WordPerfect.PRODUCT_TYPE, pa.getProductType());
        metadata.set(WordPerfect.FILE_TYPE, pa.getFileType());
        metadata.set(WordPerfect.MAJOR_VERSION, pa.getMajorVersion());
        metadata.set(WordPerfect.MINOR_VERSION, pa.getMinorVersion());
        metadata.set(WordPerfect.ENCRYPTED, Boolean.toString(pa.isEncrypted()));
        
        if (pa.getFileSize() > -1) {
            metadata.set(
                    WordPerfect.FILE_SIZE, Long.toString(pa.getFileSize()));
        }
    }
    
    private WPDocumentAreaExtractor getDocumentAreaExtractor(
            WPPrefixArea prefixArea) {
        if (prefixArea.getMajorVersion() == WPPrefixArea.WP6_MAJOR_VERSION) {
            return new WP6DocumentAreaExtractor();
        }
        // we can safely assume v5 as exception would have been thrown
        return new WP5DocumentAreaExtractor();
    }
}
