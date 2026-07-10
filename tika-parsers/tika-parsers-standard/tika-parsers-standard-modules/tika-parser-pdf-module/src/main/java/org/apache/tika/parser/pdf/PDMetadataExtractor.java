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
package org.apache.tika.parser.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.Property;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.xmp.XmpExtractor;
import org.apache.tika.utils.StringUtils;

public class PDMetadataExtractor {

    /** Parse a PDF's XMP packet into metadata via the shared, container-agnostic {@link XmpExtractor}. */
    public static void extract(PDMetadata pdMetadata, Metadata metadata, ParseContext context) {
        if (pdMetadata == null) {
            metadata.set(PDF.HAS_XMP, "false");
            return;
        }
        metadata.set(PDF.HAS_XMP, "true");
        InputStream is;
        try {
            is = pdMetadata.exportXMPMetadata();
        } catch (IOException e) {
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
            return;
        }
        try {
            extract(is, metadata, context);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    /** Shared XmpExtractor + PDF-specific fixups (octal-BOM decode, derived PDF/A version). */
    public static void extract(InputStream xmp, Metadata metadata, ParseContext context) {
        try {
            // PDF octal-BOM decode is the one XMP value fixup that is container-specific.
            new XmpExtractor(PDMetadataExtractor::decode).extract(xmp, metadata, context);
        } catch (IOException | SAXException | TikaException e) {
            EmbeddedDocumentUtil.recordException(e, metadata);
        }
        derivePDFAVersion(metadata);
    }

    /** PDF/A version is derived from the pdfaid part + conformance, e.g. {@code A-1b}. */
    private static void derivePDFAVersion(Metadata metadata) {
        String conformance = metadata.get(PDF.PDFAID_CONFORMANCE);
        if (conformance == null) {
            return;
        }
        String part = metadata.get(PDF.PDFAID_PART);
        String partString = (part == null) ? "UNKNOWN" : part;
        metadata.set(PDF.PDFA_VERSION, "A-" + partString + conformance.toLowerCase(Locale.ROOT));
    }

    static void addNotNull(String value, Metadata metadata, Property... properties) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        for (Property property : properties) {
            metadata.add(property, value);
        }
    }

    /**
     * Add non-null, non-empty and unique values to the Metadata object. If the property
     * does not allow multiple values, silently fail to add values after the first.
     */
    static void addMetadata(Metadata metadata, Property property, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String decoded = decode(value);
        if (StringUtils.isBlank(decoded)) {
            return;
        }
        if (property.isMultiValuePermitted() || metadata.get(property) == null) {
            for (String v : metadata.getValues(property)) {
                if (v.equals(decoded)) {
                    return;
                }
            }
            metadata.add(property, decoded);
        }
        //silently skip adding property that already exists if multiple values are not permitted
    }

    static void addMetadata(Metadata metadata, String name, String value) {
        if (value != null) {
            String decoded = decode(value);
            if (StringUtils.isBlank(decoded)) {
                return;
            }
            for (String v : metadata.getValues(name)) {
                if (v.equals(decoded)) {
                    return;
                }
            }
            metadata.add(name, decoded);
        }
    }

    static String decode(String value) {
        if (PDFEncodedStringDecoder.shouldDecode(value)) {
            PDFEncodedStringDecoder d = new PDFEncodedStringDecoder();
            return d.decode(value);
        }
        return value;
    }

    static void addMetadata(Metadata metadata, Property property, Calendar value) {
        if (value != null) {
            metadata.set(property, value);
        }
    }

    /**
     * Used when processing custom metadata entries, as PDFBox won't do
     * the conversion for us in the way it does for the standard ones
     */
    static void addMetadata(Metadata metadata, String name, COSBase value) {
        if (value instanceof COSArray) {
            for (Object v : ((COSArray) value).toList()) {
                addMetadata(metadata, name, ((COSBase) v));
            }
        } else if (value instanceof COSString) {
            addMetadata(metadata, name, ((COSString) value).getString());
        }
        // Avoid calling COSDictionary#toString, since it can lead to infinite
        // recursion. See TIKA-1038 and PDFBOX-1835.
        else if (value != null && !(value instanceof COSDictionary)) {
            addMetadata(metadata, name, value.toString());
        }
    }
}
