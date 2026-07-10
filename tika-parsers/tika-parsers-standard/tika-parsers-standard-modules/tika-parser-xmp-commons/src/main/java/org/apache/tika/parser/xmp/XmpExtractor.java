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
package org.apache.tika.parser.xmp;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Google;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPDC;
import org.apache.tika.metadata.XMPMM;
import org.apache.tika.metadata.XMPPDF;
import org.apache.tika.metadata.XMPRights;
import org.apache.tika.parser.ParseContext;

/**
 * Container-agnostic XMP extractor: SAX-flatten a packet, map to canonical Tika
 * properties, normalize dates. Same mapping regardless of source container.
 */
public class XmpExtractor {

    private static final String RESOURCE_EVENT_NS = "http://ns.adobe.com/xap/1.0/sType/ResourceEvent#";
    private static final String RESOURCE_REF_NS = "http://ns.adobe.com/xap/1.0/sType/ResourceRef#";

    // (namespaceURI, localName) -> Tika properties (canonical + xmp-namespaced, Option A double-keying)
    private static final Map<String, Property[]> TABLE = new HashMap<>();
    // ResourceEvent field -> xmpMM:History parallel bag
    private static final Map<String, Property> HISTORY = new HashMap<>();
    // ResourceRef field (within DerivedFrom) -> property
    private static final Map<String, Property> DERIVED_FROM = new HashMap<>();
    // Google camera localName -> property
    private static final Map<String, Property> GOOGLE_CAMERA = new HashMap<>();
    // "uri localName" -> canonical date filled set-if-absent (docinfo/EXIF still win)
    private static final Map<String, Property> FILL_IF_ABSENT = new HashMap<>();
    // "uri localName" keys that are neither mapped nor passed through as raw (known junk/bloat)
    private static final Set<String> DROP = new HashSet<>();
    // Unmapped XMP is exposed under this prefix so raw, untrusted keys can never shadow a known
    // Tika field or an X-TIKA: control key.
    static final String RAW_PREFIX = "xmp-raw:";

    private static void put(String uri, String localName, Property... props) {
        TABLE.put(uri + " " + localName, props);
    }

    static {
        String dc = DublinCore.NAMESPACE_URI_DC;
        put(dc, "title", TikaCoreProperties.TITLE, XMPDC.TITLE);
        put(dc, "creator", TikaCoreProperties.CREATOR, XMPDC.CREATOR);
        put(dc, "subject", TikaCoreProperties.SUBJECT, XMPDC.SUBJECT);
        put(dc, "description", TikaCoreProperties.DESCRIPTION, XMPDC.DESCRIPTION);
        put(dc, "publisher", DublinCore.PUBLISHER, XMPDC.PUBLISHER);
        put(dc, "contributor", DublinCore.CONTRIBUTOR, XMPDC.CONTRIBUTOR);
        put(dc, "type", DublinCore.TYPE, XMPDC.TYPE);
        // dc:format and dc:date map to the xmp-namespaced key only: the canonical FORMAT holds the
        // real MIME type, and dc:date is an ambiguous date bag. The unambiguous xmp:CreateDate /
        // ModifyDate fill the canonical created/modified instead (set-if-absent, below).
        put(dc, "format", XMPDC.FORMAT);
        put(dc, "identifier", DublinCore.IDENTIFIER, XMPDC.IDENTIFIER);
        put(dc, "language", DublinCore.LANGUAGE, XMPDC.LANGUAGE);
        put(dc, "relation", DublinCore.RELATION, XMPDC.RELATION);
        put(dc, "source", DublinCore.SOURCE, XMPDC.SOURCE);
        put(dc, "coverage", DublinCore.COVERAGE, XMPDC.COVERAGE);
        put(dc, "rights", DublinCore.RIGHTS, XMPDC.RIGHTS);
        put(dc, "date", XMPDC.DATE);

        String xmp = XMP.NAMESPACE_URI;
        put(xmp, "Title", TikaCoreProperties.TITLE, XMP.TITLE);
        put(xmp, "CreateDate", XMP.CREATE_DATE);
        put(xmp, "ModifyDate", XMP.MODIFY_DATE);
        put(xmp, "MetadataDate", XMP.METADATA_DATE);
        put(xmp, "CreatorTool", XMP.CREATOR_TOOL);
        put(xmp, "Rating", XMP.RATING);
        put(xmp, "Label", XMP.LABEL);
        put(xmp, "Nickname", XMP.NICKNAME);
        put(xmp, "Identifier", XMP.IDENTIFIER);
        put(xmp, "Advisory", XMP.ADVISORY);

        String mm = XMPMM.NAMESPACE_URI;
        put(mm, "DocumentID", XMPMM.DOCUMENTID);
        put(mm, "InstanceID", XMPMM.INSTANCEID);
        put(mm, "OriginalDocumentID", XMPMM.ORIGINAL_DOCUMENTID);
        put(mm, "RenditionClass", XMPMM.RENDITION_CLASS);
        put(mm, "RenditionParams", XMPMM.RENDITION_PARAMS);

        String pdf = "http://ns.adobe.com/pdf/1.3/";
        put(pdf, "Producer", PDF.PRODUCER, XMPPDF.PRODUCER);
        put(pdf, "Keywords", Office.KEYWORDS, XMPPDF.KEY_WORDS);
        put(pdf, "PDFVersion", PDF.PDF_VERSION, XMPPDF.PDF_VERSION);

        String rights = XMPRights.NAMESPACE_URI_XMP_RIGHTS;
        put(rights, "Marked", XMPRights.MARKED);
        put(rights, "WebStatement", XMPRights.WEB_STATEMENT);
        put(rights, "UsageTerms", XMPRights.USAGE_TERMS);
        put(rights, "Owner", XMPRights.OWNER);
        put(rights, "Certificate", XMPRights.CERTIFICATE);

        // PDF-only namespaces: single canonical home in PDF.*, no dc-style duplicate-key question.
        put("http://www.aiim.org/pdfa/ns/id/", "part", PDF.PDFAID_PART);
        put("http://www.aiim.org/pdfa/ns/id/", "conformance", PDF.PDFAID_CONFORMANCE);
        put("http://www.aiim.org/pdfua/ns/id/", "part", PDF.PDFUAID_PART);
        put("http://ns.adobe.com/pdfx/1.3/", "GTS_PDFXVersion", PDF.PDFX_VERSION);
        put("http://ns.adobe.com/pdfx/1.3/", "GTS_PDFXConformance", PDF.PDFX_CONFORMANCE);
        put("http://www.npes.org/pdfx/ns/id/", "GTS_PDFXVersion", PDF.PDFXID_VERSION);
        put("http://www.npes.org/pdfvt/ns/id/", "GTS_PDFVTVersion", PDF.PDFVT_VERSION);
        put("http://www.npes.org/pdfvt/ns/id/", "GTS_PDFVTModDate", PDF.PDFVT_MODIFIED);
        put("http://ns.adobe.com/illustrator/1.0/", "Type", PDF.ILLUSTRATOR_TYPE);

        HISTORY.put("action", XMPMM.HISTORY_ACTION);
        HISTORY.put("when", XMPMM.HISTORY_WHEN);
        HISTORY.put("instanceID", XMPMM.HISTORY_EVENT_INSTANCEID);
        HISTORY.put("softwareAgent", XMPMM.HISTORY_SOFTWARE_AGENT);
        HISTORY.put("changed", XMPMM.HISTORY_CHANGED);
        HISTORY.put("parameters", XMPMM.HISTORY_PARAMETERS);

        DERIVED_FROM.put("documentID", XMPMM.DERIVED_FROM_DOCUMENTID);
        DERIVED_FROM.put("instanceID", XMPMM.DERIVED_FROM_INSTANCEID);
        DERIVED_FROM.put("originalDocumentID", XMPMM.DERIVED_FROM_ORIGINAL_DOCUMENTID);
        DERIVED_FROM.put("renditionClass", XMPMM.DERIVED_FROM_RENDITION_CLASS);

        GOOGLE_CAMERA.put("MotionPhoto", Google.MOTION_PHOTO);
        GOOGLE_CAMERA.put("MotionPhotoVersion", Google.MOTION_PHOTO_VERSION);
        GOOGLE_CAMERA.put("MotionPhotoPresentationTimestampUs", Google.MOTION_PHOTO_PRESENTATION_TIMESTAMP_US);
        GOOGLE_CAMERA.put("MicroVideo", Google.MICRO_VIDEO);
        GOOGLE_CAMERA.put("MicroVideoVersion", Google.MICRO_VIDEO_VERSION);
        GOOGLE_CAMERA.put("MicroVideoOffset", Google.MICRO_VIDEO_OFFSET);
        GOOGLE_CAMERA.put("MicroVideoPresentationTimestampUs", Google.MICRO_VIDEO_PRESENTATION_TIMESTAMP_US);

        // xmp:CreateDate/ModifyDate are the standard XMP dates; besides their own xmp-namespaced
        // keys they fill dcterms:created/modified set-if-absent, so an XMP-only file (no docinfo
        // or binary EXIF date) is not left dateless. When the photoshop:/exif: namespaces are
        // promoted, photoshop:DateCreated and exif:DateTimeOriginal belong here as fallbacks.
        FILL_IF_ABSENT.put(xmp + " CreateDate", TikaCoreProperties.CREATED);
        FILL_IF_ABSENT.put(xmp + " ModifyDate", TikaCoreProperties.MODIFIED);

        // tiff/exif:NativeDigest is Adobe's internal check that the XMP is still in sync with the
        // legacy EXIF/TIFF block (a tag-id list + MD5); meaningless to consumers -> drop.
        DROP.add("http://ns.adobe.com/tiff/1.0/ NativeDigest");
        DROP.add("http://ns.adobe.com/exif/1.0/ NativeDigest");
        // TODO: the thumbnail is a base64-encoded JPEG; route it through embedded-document
        // extraction instead of discarding the bytes. For now drop the blob (it bloats every
        // metadata dump) but keep its xmpGImg:width/height/format siblings so the thumbnail's
        // existence stays visible.
        DROP.add("http://ns.adobe.com/xap/1.0/g/img/ image");
    }

    private final XmpSaxFlattener flattener = new XmpSaxFlattener();
    // container-specific value fixup (e.g. PDF octal-BOM decode); identity for XMP that needs none
    private final UnaryOperator<String> valueDecoder;

    public XmpExtractor() {
        this(UnaryOperator.identity());
    }

    public XmpExtractor(UnaryOperator<String> valueDecoder) {
        this.valueDecoder = valueDecoder == null ? UnaryOperator.identity() : valueDecoder;
    }

    public void extract(byte[] packet, Metadata metadata)
            throws IOException, TikaException, SAXException {
        extract(packet, metadata, new ParseContext());
    }

    public void extract(byte[] packet, Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {
        map(flattener.flatten(packet, context), metadata);
    }

    public void extract(InputStream packet, Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {
        map(flattener.flatten(packet, context), metadata);
    }

    private void map(List<XmpProperty> props, Metadata metadata) {
        for (XmpProperty p : props) {
            map(p, metadata);
        }
    }

    private void map(XmpProperty p, Metadata metadata) {
        String uri = p.namespaceURI;
        String ln = p.localName();

        if (XmpSaxFlattener.XMLNS.equals(uri)) {
            return;   // xml:lang qualifier leaf — structural, not content
        }
        if (DROP.contains(uri + " " + ln)) {
            return;   // Adobe-internal digest or base64 thumbnail blob: neither mapped nor raw
        }
        String value = valueDecoder.apply(p.value);   // container-specific fixup (e.g. PDF octal-BOM)
        if (value == null || value.isEmpty()) {
            return;
        }
        if (RESOURCE_EVENT_NS.equals(uri) && p.path.contains("History[")) {
            Property hp = HISTORY.get(ln);
            if (hp != null) {
                // History:When is a text bag but holds a date; normalize it like a date field.
                if ("when".equals(ln)) {
                    String n = XmpDates.normalize(value);
                    if (n != null) {
                        value = n;
                    }
                }
                emit(metadata, hp, value);
                return;
            }   // unmapped History field (changed, parameters, ...) -> passthrough below
        }
        if (RESOURCE_REF_NS.equals(uri) && p.path.contains("DerivedFrom")) {
            Property dp = DERIVED_FROM.get(ln);
            if (dp != null) {
                emit(metadata, dp, value);
                return;
            }   // unmapped ref field (renditionClass, ...) -> passthrough below
        }
        if (Google.CAMERA_NS.equals(uri)) {
            Property g = GOOGLE_CAMERA.get(ln);
            if (g != null) {
                if (metadata.get(g) == null) {
                    metadata.set(g, value);
                }
                return;
            }
        }
        Property fill = FILL_IF_ABSENT.get(uri + " " + ln);
        if (fill != null && metadata.get(fill) == null) {
            metadata.set(fill, valueFor(fill, value));   // canonical date, only if none yet
        }
        Property[] props = TABLE.get(uri + " " + ln);
        if (props != null) {
            for (Property prop : props) {
                emit(metadata, prop, value);
                if (p.lang != null && !p.lang.isEmpty()) {
                    metadata.add(prop.getName() + ":" + p.lang, valueFor(prop, value));   // e.g. dc:title:es
                }
            }
            return;
        }
        metadata.add(RAW_PREFIX + p.path, value);   // unmapped: namespaced raw passthrough
    }

    private void emit(Metadata metadata, Property prop, String value) {
        if (prop == null) {
            return;
        }
        String v = valueFor(prop, value);
        if (prop.isMultiValuePermitted()) {
            metadata.add(prop, v);
        } else {
            metadata.set(prop, v);
        }
    }

    private static String valueFor(Property prop, String value) {
        if (prop.getValueType() == Property.ValueType.DATE) {
            String norm = XmpDates.normalize(value);
            if (norm != null) {
                return norm;
            }
        }
        return value;
    }
}
