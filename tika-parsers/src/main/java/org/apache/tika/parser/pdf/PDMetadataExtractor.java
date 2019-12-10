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

import org.apache.jempbox.xmp.XMPMetadata;
import org.apache.jempbox.xmp.XMPSchema;
import org.apache.jempbox.xmp.XMPSchemaDublinCore;
import org.apache.jempbox.xmp.pdfa.XMPSchemaPDFAId;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.poi.util.IOUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.image.xmp.JempboxExtractor;
import org.apache.tika.utils.XMLReaderUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

class PDMetadataExtractor {

    private static final MediaType MEDIA_TYPE = MediaType.application("pdf");


    static void extract(PDMetadata pdMetadata, Metadata metadata, ParseContext context) {
        if (pdMetadata == null) {
            metadata.set(PDF.HAS_XMP, "false");
            return;
        }
        //this file has XMP...
        //whether or not it is readable or throws an exception is another story...
        metadata.set(PDF.HAS_XMP, "true");
        //now go for the XMP
        Document dom = loadDOM(pdMetadata, metadata, context);

        XMPMetadata xmp = null;
        if (dom != null) {
            xmp = new XMPMetadata(dom);
        }
        XMPSchemaDublinCore dcSchema = null;

        if (xmp != null) {
            try {
                dcSchema = xmp.getDublinCoreSchema();
            } catch (IOException e) {
            }

            JempboxExtractor.extractXMPMM(xmp, metadata);
        }

        extractMultilingualItems(metadata, TikaCoreProperties.DESCRIPTION, null, dcSchema);
        extractDublinCoreListItems(metadata, TikaCoreProperties.CONTRIBUTOR, dcSchema);
        extractDublinCoreListItems(metadata, TikaCoreProperties.CREATOR, dcSchema);
        extractMultilingualItems(metadata, TikaCoreProperties.TITLE, null, dcSchema);

        try {
            if (xmp != null) {
                xmp.addXMLNSMapping(XMPSchemaPDFAId.NAMESPACE, XMPSchemaPDFAId.class);
                XMPSchemaPDFAId pdfaxmp = (XMPSchemaPDFAId) xmp.getSchemaByClass(XMPSchemaPDFAId.class);
                if (pdfaxmp != null) {
                    if (pdfaxmp.getPart() != null) {
                        metadata.set(PDF.PDFAID_PART, Integer.toString(pdfaxmp.getPart()));
                    }
                    if (pdfaxmp.getConformance() != null) {
                        metadata.set(PDF.PDFAID_CONFORMANCE, pdfaxmp.getConformance());
                        String version = "A-" + pdfaxmp.getPart() + pdfaxmp.getConformance().toLowerCase(Locale.ROOT);
                        metadata.set(PDF.PDFA_VERSION, version);
                        metadata.add(TikaCoreProperties.FORMAT.getName(),
                                MEDIA_TYPE.toString() + "; version=\"" + version + "\"");
                    }
                }
                // TODO WARN if this XMP version is inconsistent with document header version?
            }
        } catch (IOException e) {
            metadata.set(TikaCoreProperties.TIKA_META_PREFIX + "pdf:metadata-xmp-parse-failed", "" + e);
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
     * For now, this only extracts the first language if the property does not allow multiple values (see TIKA-1295)
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
    private static void extractDublinCoreListItems(Metadata metadata, Property property, XMPSchemaDublinCore dc) {
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
            if (property.isMultiValuePermitted() || metadata.get(property) == null) {
                metadata.add(property, decoded);
            }
            //silently skip adding property that already exists if multiple values are not permitted
        }
    }

    static void addMetadata(Metadata metadata, String name, String value) {
        if (value != null) {
            metadata.add(name, decode(value));
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
    private static Document loadDOM(PDMetadata pdMetadata, Metadata metadata, ParseContext context) {
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
        } catch (IOException| SAXException | TikaException e) {
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
