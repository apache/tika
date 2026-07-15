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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Google;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Photoshop;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPDC;
import org.apache.tika.metadata.XMPMM;
import org.apache.tika.metadata.XMPPDF;
import org.apache.tika.metadata.XMPRights;
import org.apache.tika.metadata.XMPTIFF;
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
    // Unmapped XMP goes under this prefix so raw, untrusted keys can't shadow a known Tika or
    // X-TIKA: field. Best-effort discovery surface: keys use the document's prefix (not the URI)
    // and are non-contractual -- promote a field into TABLE when it needs a stable key.
    static final String RAW_PREFIX = "xmp-raw:";
    // strip a trailing array index so a raw bag/seq is one multi-valued key, not foo:Bag[1], foo:Bag[2]
    private static final Pattern TRAILING_INDEX = Pattern.compile("\\[\\d+\\]$");

    // History parallel bags, fixed order; emitHistory pads absent fields so bag[i] stays aligned.
    private static final Property[] HISTORY_PROPS = {
            XMPMM.HISTORY_ACTION, XMPMM.HISTORY_WHEN, XMPMM.HISTORY_EVENT_INSTANCEID,
            XMPMM.HISTORY_SOFTWARE_AGENT, XMPMM.HISTORY_CHANGED, XMPMM.HISTORY_PARAMETERS,
    };
    // hard cap on history events exposed, so a hostile packet can't inflate metadata without bound
    private static final int MAX_HISTORY_EVENTS = 1024;

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
        // dc:format/dc:date -> xmp key only: canonical FORMAT is the real MIME type, dc:date is
        // ambiguous; the unambiguous xmp:CreateDate/ModifyDate fill canonical created/modified below.
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
        // rdf:about (the packet's document URI) -> xmp:About; flattener emits it only when non-empty
        put(XmpSaxFlattener.RDF, "about", XMP.ABOUT);

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

        // Promoted from raw passthrough; these namespaces are XMP-exclusive, so one canonical key suffices.
        String ps = Photoshop.NAMESPACE_URI_PHOTOSHOP;
        put(ps, "ColorMode", Photoshop.COLOR_MODE);
        put(ps, "ICCProfile", Photoshop.ICC_PROFILE);
        put(ps, "DateCreated", Photoshop.DATE_CREATED);
        put("http://ns.adobe.com/xap/1.0/t/pg/", "NPages", PagedText.N_PAGES);
        put(pdf, "Trapped", PDF.TRAPPED);

        // Camera/EXIF/TIFF -> shared TIFF interface. tiff:/exif: double-keyed: the canonical key may
        // also come from binary EXIF (extracted later, so it wins); the XMPTIFF key keeps XMP
        // provenance. aux: is XMP-only -> single key. Rationals/ResolutionUnit/Flash stay raw pending
        // value normalization.
        String aux = "http://ns.adobe.com/exif/1.0/aux/";
        put(aux, "SerialNumber", TIFF.SERIAL_NUMBER);
        put(aux, "Lens", TIFF.LENS);
        put(aux, "LensInfo", TIFF.LENS_INFO);
        put(aux, "LensID", TIFF.LENS_ID);
        String tiff = "http://ns.adobe.com/tiff/1.0/";
        put(tiff, "Make", TIFF.EQUIPMENT_MAKE, XMPTIFF.EQUIPMENT_MAKE);
        put(tiff, "Model", TIFF.EQUIPMENT_MODEL, XMPTIFF.EQUIPMENT_MODEL);
        put(tiff, "Software", TIFF.SOFTWARE, XMPTIFF.SOFTWARE);
        put(tiff, "ImageWidth", TIFF.IMAGE_WIDTH, XMPTIFF.IMAGE_WIDTH);
        put(tiff, "ImageLength", TIFF.IMAGE_LENGTH, XMPTIFF.IMAGE_LENGTH);
        put(tiff, "BitsPerSample", TIFF.BITS_PER_SAMPLE, XMPTIFF.BITS_PER_SAMPLE);
        put(tiff, "SamplesPerPixel", TIFF.SAMPLES_PER_PIXEL, XMPTIFF.SAMPLES_PER_PIXEL);
        put(tiff, "Orientation", TIFF.ORIENTATION, XMPTIFF.ORIENTATION);
        String exif = "http://ns.adobe.com/exif/1.0/";
        put(exif, "DateTimeOriginal", TIFF.ORIGINAL_DATE, XMPTIFF.ORIGINAL_DATE);
        put(exif, "ISOSpeedRatings", TIFF.ISO_SPEED_RATINGS, XMPTIFF.ISO_SPEED_RATINGS);
        // exif:Pixel*Dimension -> same IMAGE_WIDTH/LENGTH as binary EXIF (integers, no format question).
        put(exif, "PixelXDimension", TIFF.IMAGE_WIDTH, XMPTIFF.PIXEL_X_DIMENSION);
        put(exif, "PixelYDimension", TIFF.IMAGE_LENGTH, XMPTIFF.PIXEL_Y_DIMENSION);

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

        // Standard XMP dates fill dcterms:created/modified set-if-absent, so an XMP-only file (no
        // docinfo or binary EXIF date) isn't left dateless; photoshop:/exif: dates are fallbacks.
        FILL_IF_ABSENT.put(xmp + " CreateDate", TikaCoreProperties.CREATED);
        FILL_IF_ABSENT.put(xmp + " ModifyDate", TikaCoreProperties.MODIFIED);
        FILL_IF_ABSENT.put(ps + " DateCreated", TikaCoreProperties.CREATED);      // fallback creation date
        FILL_IF_ABSENT.put(exif + " DateTimeOriginal", TikaCoreProperties.CREATED);

        // NativeDigest is Adobe's XMP<->legacy-EXIF/TIFF sync check (tag-ids + MD5); useless to consumers.
        DROP.add("http://ns.adobe.com/tiff/1.0/ NativeDigest");
        DROP.add("http://ns.adobe.com/exif/1.0/ NativeDigest");
        // TODO: base64 JPEG thumbnail -> route through embedded-doc extraction. For now drop the blob
        // (it bloats every dump) but keep the xmpGImg:width/height/format siblings so it stays visible.
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
        // History and rdf:Alt lang sets are handled as groups (aligned bags; Alt canonical = x-default);
        // everything else streams one leaf at a time.
        emitHistory(props, metadata);
        emitLangAlternatives(props, metadata);
        for (XmpProperty p : props) {
            if (!isMappedHistoryEvent(p) && !isLangAlternative(p)) {
                map(p, metadata);
            }
        }
    }

    private boolean isMappedHistoryEvent(XmpProperty p) {
        return RESOURCE_EVENT_NS.equals(p.namespaceURI) && p.path.contains("History[")
                && HISTORY.containsKey(p.localName());
    }

    /** A mapped, top-level, language-tagged leaf is an rdf:Alt alternative (dc:title, ...). */
    private boolean isLangAlternative(XmpProperty p) {
        return p.lang != null && !p.lang.isEmpty() && p.path.indexOf('/') < 0
                && TABLE.containsKey(p.namespaceURI + " " + p.localName());
    }

    private static int historyIndex(String path) {
        int i = path.indexOf("History[");
        if (i < 0) {
            return -1;
        }
        int start = i + "History[".length();
        int end = path.indexOf(']', start);
        if (end < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(path.substring(start, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Emit the xmpMM:History parallel bags per event, index-aligned, capped, dates normalized. */
    private void emitHistory(List<XmpProperty> props, Metadata metadata) {
        TreeMap<Integer, Map<Property, String>> events = new TreeMap<>();
        for (XmpProperty p : props) {
            if (!isMappedHistoryEvent(p)) {
                continue;
            }
            int idx = historyIndex(p.path);
            String value = valueDecoder.apply(p.value);
            if (idx < 0 || value == null || value.isEmpty()) {
                continue;
            }
            if ("when".equals(p.localName())) {   // a text bag that holds a date -> normalize it
                String n = XmpDates.normalize(value);
                if (n != null) {
                    value = n;
                }
            }
            events.computeIfAbsent(idx, k -> new HashMap<>()).put(HISTORY.get(p.localName()), value);
        }
        // only emit the bags that actually occur, but keep those index-aligned across events
        Set<Property> present = new LinkedHashSet<>();
        for (Map<Property, String> ev : events.values()) {
            present.addAll(ev.keySet());
        }
        int count = 0;
        for (Map<Property, String> ev : events.values()) {
            if (++count > MAX_HISTORY_EVENTS) {
                break;
            }
            for (Property hp : HISTORY_PROPS) {
                if (present.contains(hp)) {
                    metadata.add(hp, ev.getOrDefault(hp, ""));
                }
            }
        }
    }

    /** rdf:Alt language sets: the canonical value is x-default (else first); every lang keeps a key. */
    private void emitLangAlternatives(List<XmpProperty> props, Metadata metadata) {
        Map<String, List<XmpProperty>> byKey = new LinkedHashMap<>();
        for (XmpProperty p : props) {
            if (isLangAlternative(p)) {
                byKey.computeIfAbsent(p.namespaceURI + " " + p.localName(),
                        k -> new ArrayList<>()).add(p);
            }
        }
        for (Map.Entry<String, List<XmpProperty>> e : byKey.entrySet()) {
            List<XmpProperty> alts = e.getValue();
            XmpProperty primary = alts.get(0);
            for (XmpProperty a : alts) {
                if ("x-default".equals(a.lang)) {
                    primary = a;
                    break;
                }
            }
            String primaryValue = valueDecoder.apply(primary.value);
            for (Property prop : TABLE.get(e.getKey())) {
                if (prop.isMultiValuePermitted()) {
                    // a text bag keeps every language on the canonical key (TIKA-1295 / TIKA-4466),
                    // but x-default leads so metadata.get(prop) -- which returns values[0] -- yields
                    // the default, not whatever language the packet happened to list first.
                    if (primaryValue != null && !primaryValue.isEmpty()) {
                        emit(metadata, prop, primaryValue);
                    }
                    for (XmpProperty a : alts) {
                        if (a == primary) {
                            continue;
                        }
                        String v = valueDecoder.apply(a.value);
                        if (v != null && !v.isEmpty()) {
                            emit(metadata, prop, v);
                        }
                    }
                } else if (primaryValue != null && !primaryValue.isEmpty()) {
                    emit(metadata, prop, primaryValue);   // single-valued: x-default, not last-wins
                }
                for (XmpProperty a : alts) {   // every language variant keeps its own key
                    String v = valueDecoder.apply(a.value);
                    if (v != null && !v.isEmpty()) {
                        metadata.add(prop.getName() + ":" + a.lang, valueFor(prop, v));
                    }
                }
            }
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
        // xmpMM:History is handled as a group in emitHistory() (parallel bags stay aligned).
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
        // Map (uri, localName) only at the top level: a property nested in a struct (e.g. xmpMM:InstanceID
        // inside xmpMM:Pantry) must not overwrite the document-level one. Struct segments join with '/',
        // array indices don't, so top-level has no '/'; nested falls through to raw passthrough.
        boolean topLevel = p.path.indexOf('/') < 0;
        Property fill = FILL_IF_ABSENT.get(uri + " " + ln);
        if (topLevel && fill != null && metadata.get(fill) == null) {
            metadata.set(fill, valueFor(fill, value));   // canonical date, only if none yet
        }
        // Language alternatives (leaves with an xml:lang) are handled in emitLangAlternatives().
        Property[] props = TABLE.get(uri + " " + ln);
        if (topLevel && props != null) {
            for (Property prop : props) {
                emit(metadata, prop, value);
            }
            return;
        }
        // unmapped: namespaced raw passthrough; a trailing array index collapses to a multi-valued key
        metadata.add(RAW_PREFIX + TRAILING_INDEX.matcher(p.path).replaceFirst(""), value);
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
