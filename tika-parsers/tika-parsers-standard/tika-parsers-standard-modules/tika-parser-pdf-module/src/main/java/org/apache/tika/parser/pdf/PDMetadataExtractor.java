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
import java.util.List;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.apache.jempbox.xmp.XMPMetadata;
import org.apache.jempbox.xmp.XMPSchema;
import org.apache.jempbox.xmp.XMPSchemaBasic;
import org.apache.jempbox.xmp.XMPSchemaDublinCore;
import org.apache.jempbox.xmp.XMPSchemaPDF;
import org.apache.jempbox.xmp.pdfa.XMPSchemaPDFAId;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.xmpschemas.XMPSchemaIllustrator;
import org.apache.tika.parser.pdf.xmpschemas.XMPSchemaPDFUA;
import org.apache.tika.parser.pdf.xmpschemas.XMPSchemaPDFVT;
import org.apache.tika.parser.pdf.xmpschemas.XMPSchemaPDFX;
import org.apache.tika.parser.pdf.xmpschemas.XMPSchemaPDFXId;
import org.apache.tika.parser.xmp.JempboxExtractor;
import org.apache.tika.utils.StringUtils;
import org.apache.tika.utils.XMLReaderUtils;

public class PDMetadataExtractor {

    public static void extract(PDMetadata pdMetadata, Metadata metadata, ParseContext context) {
        if (pdMetadata == null) {
            metadata.set(PDF.HAS_XMP, "false");
            return;
        }
        //this file has XMP...
        //whether or not it is readable or throws an exception is another story...
        metadata.set(PDF.HAS_XMP, "true");
        //now go for the XMP
        Document dom = loadDOM(pdMetadata, metadata, context);
        if (dom == null) {
            return;
        }
        XMPMetadata xmp = new XMPMetadata(dom);
        extract(xmp, metadata, context);
    }

    public static void extract(XMPMetadata xmp, Metadata metadata, ParseContext context) {
        extractBasic(xmp, metadata);
        extractPDF(xmp, metadata);
        extractDublin(xmp, metadata);
        JempboxExtractor.extractXMPMM(xmp, metadata);
        extractPDFA(xmp, metadata);
        extractPDFX(xmp, metadata);
        extractPDFVT(xmp, metadata);
        extractPDFUA(xmp, metadata);
        extractIllustrator(xmp, metadata);
    }

    private static void extractIllustrator(XMPMetadata xmp, Metadata metadata) {
        xmp.addXMLNSMapping(XMPSchemaIllustrator.NAMESPACE_URI, XMPSchemaIllustrator.class);
        XMPSchemaIllustrator schema = null;
        try {
            schema = (XMPSchemaIllustrator) xmp.getSchemaByClass(XMPSchemaIllustrator.class);
        } catch (IOException e) {
            metadata.set(TikaCoreProperties.TIKA_META_PREFIX + "pdf:metadata-xmp-parse-failed",
                    "" + e);
        }

        if (schema == null) {
            return;
        }
        String type = schema.getType();
        if (! StringUtils.isBlank(type)) {
            metadata.set(PDF.ILLUSTRATOR_TYPE, type);
        }
    }

    private static void extractDublin(XMPMetadata xmp, Metadata metadata) {
        XMPSchemaDublinCore dcSchema = null;
        try {
            dcSchema = xmp.getDublinCoreSchema();
        } catch (IOException e) {
            //swallow
        }
        if (dcSchema != null) {
            extractMultilingualItems(metadata, TikaCoreProperties.DESCRIPTION, null, dcSchema);
            extractDublinCoreListItems(metadata, TikaCoreProperties.CONTRIBUTOR, dcSchema);
            extractDublinCoreListItems(metadata, TikaCoreProperties.CREATOR, dcSchema);
            extractMultilingualItems(metadata, TikaCoreProperties.TITLE, null, dcSchema);
        }
    }

    private static void extractPDFVT(XMPMetadata xmp, Metadata metadata) {
        xmp.addXMLNSMapping(XMPSchemaPDFVT.NAMESPACE_URI, XMPSchemaPDFVT.class);
        XMPSchemaPDFVT schema = null;
        try {
            schema = (XMPSchemaPDFVT) xmp.getSchemaByClass(XMPSchemaPDFVT.class);
        } catch (IOException e) {
            metadata.set(TikaCoreProperties.TIKA_META_PREFIX + "pdf:metadata-xmp-parse-failed",
                    "" + e);
        }

        if (schema == null) {
            return;
        }
        String version = schema.getPDFVTVersion();
        if (! StringUtils.isBlank(version)) {
            metadata.set(PDF.PDFVT_VERSION, version);
        }
        try {
            Calendar modified = schema.getPDFVTModified();
            metadata.set(PDF.PDFVT_MODIFIED, modified);
        } catch (IOException ex) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    "bad date in vt modified");
        }
    }

    private static void extractPDFX(XMPMetadata xmp, Metadata metadata) {
        xmp.addXMLNSMapping(XMPSchemaPDFXId.NAMESPACE_URI, XMPSchemaPDFXId.class);
        xmp.addXMLNSMapping(XMPSchemaPDFX.NAMESPACE_URI, XMPSchemaPDFX.class);
        try {
            XMPSchemaPDFXId
                    XMPSchemaPDFXId = (XMPSchemaPDFXId) xmp.getSchemaByClass(XMPSchemaPDFXId.class);
            if (XMPSchemaPDFXId != null) {
                String version = XMPSchemaPDFXId.getPDFXVersion();
                if (!StringUtils.isBlank(version)) {
                    metadata.set(PDF.PDFXID_VERSION, version);
                }
            }
        } catch (IOException e) {
            metadata.set(TikaCoreProperties.TIKA_META_PREFIX + "pdf:metadata-xmp-parse-failed",
                    "" + e);
        }
        try {
            XMPSchemaPDFX XMPSchemaPDFX = (XMPSchemaPDFX) xmp.getSchemaByClass(XMPSchemaPDFX.class);
            if (XMPSchemaPDFX != null) {
                String version = XMPSchemaPDFX.getPDFXVersion();
                if (!StringUtils.isBlank(version)) {
                    metadata.set(PDF.PDFX_VERSION, version);
                }
                String conformance = XMPSchemaPDFX.getPDFXConformance();
                if (!StringUtils.isBlank(version)) {
                    metadata.set(PDF.PDFX_CONFORMANCE, version);
                }
            }
        } catch (IOException e) {
            metadata.set(TikaCoreProperties.TIKA_META_PREFIX + "pdf:metadata-xmp-parse-failed",
                    "" + e);
        }

    }


    private static void extractPDFUA(XMPMetadata xmp, Metadata metadata) {
        xmp.addXMLNSMapping(XMPSchemaPDFUA.NAMESPACE_URI, XMPSchemaPDFUA.class);
        XMPSchemaPDFUA schema = null;
        try {
            schema = (XMPSchemaPDFUA) xmp.getSchemaByClass(XMPSchemaPDFUA.class);
        } catch (IOException e) {
            metadata.set(TikaCoreProperties.TIKA_META_PREFIX + "pdf:metadata-xmp-parse-failed",
                    "" + e);
        }

        if (schema == null) {
            return;
        }
        try {
            Integer part = schema.getPart();
            if (schema.getPart() != null) {
                metadata.set(PDF.PDFUAID_PART, part.intValue());
            }
        } catch (NumberFormatException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    "expected integer " + "part");
        }

    }

    private static void extractPDFA(XMPMetadata xmp, Metadata metadata) {
        xmp.addXMLNSMapping(XMPSchemaPDFAId.NAMESPACE, XMPSchemaPDFAId.class);
        XMPSchemaPDFAId schema = null;
        try {
            schema = (XMPSchemaPDFAId) xmp.getSchemaByClass(XMPSchemaPDFAId.class);
        } catch (IOException e) {
            metadata.set(TikaCoreProperties.TIKA_META_PREFIX + "pdf:metadata-xmp-parse-failed",
                    "" + e);
        }

        if (schema == null) {
            return;
        }
        String partString = "UNKNOWN";
        try {
            Integer part = schema.getPart();
            if (part != null) {
                partString = Integer.toString(part);
                metadata.set(PDF.PDFAID_PART, part.intValue());
            }
        } catch (NumberFormatException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    "expected integer " + "part");
        }
        if (schema.getConformance() != null) {
            metadata.set(PDF.PDFAID_CONFORMANCE, schema.getConformance());
            String version = "A-" + partString + schema.getConformance().toLowerCase(Locale.ROOT);
            metadata.set(PDF.PDFA_VERSION, version);
        }
    }

    private static void extractPDF(XMPMetadata xmp, Metadata metadata) {
        if (xmp == null) {
            return;
        }

        XMPSchemaPDF pdf = null;
        try {
            pdf = xmp.getPDFSchema();
        } catch (IOException e) {
            return;
        }
        if (pdf == null) {
            return;
        }
        setNotNull(PDF.PRODUCER, pdf.getProducer(), metadata);
        setNotNull(Office.KEYWORDS, pdf.getKeywords(), metadata);
        setNotNull(PDF.PDF_VERSION, pdf.getPDFVersion(), metadata);
    }

    private static void extractBasic(XMPMetadata xmp, Metadata metadata) {
        if (xmp == null) {
            return;
        }

        XMPSchemaBasic basic = null;
        try {
            basic = xmp.getBasicSchema();
        } catch (IOException e) {
            return;
        }
        if (basic == null) {
            return;
        }
        //add the elements from the basic schema if they haven't already
        //been extracted from dublin core
        setNotNull(XMP.CREATOR_TOOL, basic.getCreatorTool(), metadata);
        setNotNull(DublinCore.TITLE, basic.getTitle(), metadata);
        setNotNull(XMP.ABOUT, basic.getAbout(), metadata);
        setNotNull(XMP.LABEL, basic.getLabel(), metadata);
        try {
            setNotNull(XMP.CREATE_DATE, basic.getCreateDate(), metadata);
        } catch (IOException e) {
            //swallow
        }
        try {
            setNotNull(XMP.MODIFY_DATE, basic.getModifyDate(), metadata);
        } catch (IOException e) {
            //swallow
        }
        try {
            setNotNull(XMP.METADATA_DATE, basic.getMetadataDate(), metadata);
        } catch (IOException e) {
            //swallow
        }

        List<String> identifiers = basic.getIdentifiers();
        if (identifiers != null) {
            for (String identifier : identifiers) {
                metadata.add(XMP.IDENTIFIER, identifier);
            }
        }
        List<String> advisories = basic.getAdvisories();
        if (advisories != null) {
            for (String advisory : advisories) {
                metadata.add(XMP.ADVISORY, advisory);
            }
        }
        setNotNull(XMP.NICKNAME, basic.getNickname(), metadata);
        setNotNull(XMP.RATING, basic.getRating(), metadata);
        //TODO: find an example where basic.getThumbNail is not null
        //and figure out how to add that info
    }

    private static void setNotNull(Property property, String value, Metadata metadata) {
        if (metadata.get(property) == null && value != null && value.trim().length() > 0) {
            metadata.set(property, decode(value));
        }
    }

    private static void setNotNull(Property property, Calendar value, Metadata metadata) {
        if (metadata.get(property) == null && value != null) {
            metadata.set(property, value);
        }
    }

    private static void setNotNull(Property property, Integer value, Metadata metadata) {
        if (metadata.get(property) == null && value != null) {
            metadata.set(property, value);
        }
    }

    static void addNotNull(Property property, String value, Metadata metadata) {
        if (! StringUtils.isBlank(value)) {
            metadata.add(property, value);
        }
    }

    /**
     * As of this writing, XMPSchema can contain bags or sequence lists
     * for some attributes...despite standards documentation.
     * JempBox expects one or the other for specific attributes.
     * Until more flexibility is added to JempBox, Tika will have to handle both.
     *
     * @param schema
     * @param name
     * @return list of values or null
     */
    static List<String> getXMPBagOrSeqList(XMPSchema schema, String name) {
        List<String> ret = schema.getBagList(name);
        if (ret == null) {
            ret = schema.getSequenceList(name);
        }
        return ret;
    }

    /**
     * Try to extract all multilingual items from the XMPSchema
     * <p/>
     * This relies on the property having a valid xmp getName()
     * <p/>
     * For now, this only extracts the first language if the property does not allow multiple
     * values (see TIKA-1295)
     *
     * @param metadata
     * @param property
     * @param pdfBoxBaseline
     * @param schema
     */
    private static void extractMultilingualItems(Metadata metadata, Property property,
                                                 String pdfBoxBaseline, XMPSchema schema) {
        //if schema is null, just go with pdfBoxBaseline
        if (schema == null) {
            if (pdfBoxBaseline != null && pdfBoxBaseline.length() > 0) {
                addMetadata(metadata, property, pdfBoxBaseline);
            }
            return;
        }

        for (String lang : schema.getLanguagePropertyLanguages(property.getName())) {
            String value = schema.getLanguageProperty(property.getName(), lang);

            if (value != null && value.length() > 0) {
                //if you're going to add it below in the baseline addition, don't add it now
                if (pdfBoxBaseline != null && value.equals(pdfBoxBaseline)) {
                    continue;
                }
                addMetadata(metadata, property, value);
                if (!property.isMultiValuePermitted()) {
                    return;
                }
            }
        }

        if (pdfBoxBaseline != null && pdfBoxBaseline.length() > 0) {
            //if we've already added something above and multivalue is not permitted
            //return.
            if (!property.isMultiValuePermitted()) {
                if (metadata.get(property) != null) {
                    return;
                }
            }
            addMetadata(metadata, property, pdfBoxBaseline);
        }
    }


    /**
     * This tries to read a list from a particular property in
     * XMPSchemaDublinCore.
     * <p/>
     * Until PDFBOX-1803/TIKA-1233 are fixed, do not call this
     * on dates!
     * <p/>
     * This relies on the property having a DublinCore compliant getName()
     *
     * @param property
     * @param dc
     * @param metadata
     */
    private static void extractDublinCoreListItems(Metadata metadata, Property property,
                                                   XMPSchemaDublinCore dc) {
        //if no dc, add baseline and return
        if (dc == null) {
            return;
        }
        List<String> items = getXMPBagOrSeqList(dc, property.getName());
        if (items == null) {
            return;
        }
        for (String item : items) {
            addMetadata(metadata, property, item);
        }
    }


    static void addMetadata(Metadata metadata, Property property, String value) {
        if (value != null) {
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

    //can return null!
    private static Document loadDOM(PDMetadata pdMetadata, Metadata metadata,
                                    ParseContext context) {
        if (pdMetadata == null) {
            return null;
        }

        InputStream is = null;
        try {
            try {
                is = pdMetadata.exportXMPMetadata();
            } catch (IOException e) {
                EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
                return null;
            }
            return XMLReaderUtils.buildDOM(is, context);
        } catch (IOException | SAXException | TikaException e) {
            EmbeddedDocumentUtil.recordException(e, metadata);
        } finally {
            IOUtils.closeQuietly(is);
        }
        return null;

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
