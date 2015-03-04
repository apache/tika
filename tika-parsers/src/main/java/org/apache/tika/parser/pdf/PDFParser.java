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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.jempbox.xmp.XMPSchema;
import org.apache.jempbox.xmp.XMPSchemaDublinCore;
import org.apache.jempbox.xmp.pdfa.XMPSchemaPDFAId;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.exceptions.CryptographyException;
import org.apache.pdfbox.io.RandomAccess;
import org.apache.pdfbox.io.RandomAccessBuffer;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.AccessPermissions;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * PDF parser.
 * <p>
 * This parser can process also encrypted PDF documents if the required
 * password is given as a part of the input metadata associated with a
 * document. If no password is given, then this parser will try decrypting
 * the document using the empty password that's often used with PDFs. If
 * the PDF contains any embedded documents (for example as part of a PDF
 * package) then this parser will use the {@link EmbeddedDocumentExtractor}
 * to handle them.
 * <p>
 * As of Tika 1.6, it is possible to extract inline images with
 * the {@link EmbeddedDocumentExtractor} as if they were regular
 * attachments.  By default, this feature is turned off because of
 * the potentially enormous number and size of inline images.  To
 * turn this feature on, see
 * {@link PDFParserConfig#setExtractInlineImages(boolean)}.
 */
public class PDFParser extends AbstractParser {


    private static final MediaType MEDIA_TYPE = MediaType.application("pdf");

    /** Serial version UID */
    private static final long serialVersionUID = -752276948656079347L;

    private PDFParserConfig defaultConfig = new PDFParserConfig();
    /**
     * Metadata key for giving the document password to the parser.
     *
     * @since Apache Tika 0.5
     * @deprecated Supply a {@link PasswordProvider} on the {@link ParseContext} instead
     */
    public static final String PASSWORD = "org.apache.tika.parser.pdf.password";

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.singleton(MEDIA_TYPE);

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
       
        PDDocument pdfDocument = null;
        TemporaryResources tmp = new TemporaryResources();
        //config from context, or default if not set via context
        PDFParserConfig localConfig = context.get(PDFParserConfig.class, defaultConfig);
        String password = "";
        try {
            // PDFBox can process entirely in memory, or can use a temp file
            //  for unpacked / processed resources
            // Decide which to do based on if we're reading from a file or not already
            TikaInputStream tstream = TikaInputStream.cast(stream);
            password = getPassword(metadata, context);
            if (tstream != null && tstream.hasFile()) {
                // File based, take that as a cue to use a temporary file
                RandomAccess scratchFile = new RandomAccessFile(tmp.createTemporaryFile(), "rw");
                if (localConfig.getUseNonSequentialParser() == true) {
                    pdfDocument = PDDocument.loadNonSeq(new CloseShieldInputStream(stream), scratchFile, password);
                } else {
                    pdfDocument = PDDocument.load(new CloseShieldInputStream(stream), scratchFile, true);
                }
            } else {
                // Go for the normal, stream based in-memory parsing
                if (localConfig.getUseNonSequentialParser() == true) {
                    pdfDocument = PDDocument.loadNonSeq(new CloseShieldInputStream(stream), new RandomAccessBuffer(), password);
                } else {
                    pdfDocument = PDDocument.load(new CloseShieldInputStream(stream), true);
                }
            }
            metadata.set("pdf:encrypted", Boolean.toString(pdfDocument.isEncrypted()));

            //if using the classic parser and the doc is encrypted, we must manually decrypt
            if (! localConfig.getUseNonSequentialParser() && pdfDocument.isEncrypted()) {
                pdfDocument.decrypt(password);
            }

            metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
            extractMetadata(pdfDocument, metadata);

            AccessChecker checker = localConfig.getAccessChecker();
            checker.check(metadata);
            if (handler != null) {
                PDF2XHTML.process(pdfDocument, handler, context, metadata, localConfig);
            }
            
        } catch (CryptographyException e) {
            //seq parser throws CryptographyException for bad password
            throw new EncryptedDocumentException(e);
        } catch (IOException e) {
            //nonseq parser throws IOException for bad password
            //At the Tika level, we want the same exception to be thrown
            if (e.getMessage().contains("Error (CryptographyException)")) {
                metadata.set("pdf:encrypted", Boolean.toString(true));
                throw new EncryptedDocumentException(e);
            }
            //rethrow any other IOExceptions
            throw e;
        } finally {
            if (pdfDocument != null) {
               pdfDocument.close();
            }
            tmp.dispose();
            //TODO: once we migrate to PDFBox 2.0, remove this (PDFBOX-2200)
            PDFont.clearResources();
        }
    }

    private String getPassword(Metadata metadata, ParseContext context) {
        String password = null;

        // Did they supply a new style Password Provider?
        PasswordProvider passwordProvider = context.get(PasswordProvider.class);
        if (passwordProvider != null) {
            password = passwordProvider.getPassword(metadata);
        }

        // Fall back on the old style metadata if set
        if (password == null && metadata.get(PASSWORD) != null) {
            password = metadata.get(PASSWORD);
        }

        // If no password is given, use an empty string as the default
        if (password == null) {
            password = "";
        }
        return password;
    }


    private void extractMetadata(PDDocument document, Metadata metadata)
            throws TikaException {

        //first extract AccessPermissions
        AccessPermission ap = document.getCurrentAccessPermission();
        metadata.set(AccessPermissions.EXTRACT_FOR_ACCESSIBILITY,
                Boolean.toString(ap.canExtractForAccessibility()));
        metadata.set(AccessPermissions.EXTRACT_CONTENT,
                Boolean.toString(ap.canExtractContent()));
        metadata.set(AccessPermissions.ASSEMBLE_DOCUMENT,
                Boolean.toString(ap.canAssembleDocument()));
        metadata.set(AccessPermissions.FILL_IN_FORM,
                Boolean.toString(ap.canFillInForm()));
        metadata.set(AccessPermissions.CAN_MODIFY,
                Boolean.toString(ap.canModify()));
        metadata.set(AccessPermissions.CAN_MODIFY_ANNOTATIONS,
                Boolean.toString(ap.canModifyAnnotations()));
        metadata.set(AccessPermissions.CAN_PRINT,
                Boolean.toString(ap.canPrint()));
        metadata.set(AccessPermissions.CAN_PRINT_DEGRADED,
                Boolean.toString(ap.canPrintDegraded()));



        //now go for the XMP stuff
        org.apache.jempbox.xmp.XMPMetadata xmp = null;
        XMPSchemaDublinCore dcSchema = null;
        try{
            if (document.getDocumentCatalog().getMetadata() != null) {
                xmp = document.getDocumentCatalog().getMetadata().exportXMPMetadata();
            }
            if (xmp != null) {
                dcSchema = xmp.getDublinCoreSchema();
            }
        } catch (IOException e) {
            //swallow
        }
        PDDocumentInformation info = document.getDocumentInformation();
        metadata.set(PagedText.N_PAGES, document.getNumberOfPages());
        extractMultilingualItems(metadata, TikaCoreProperties.TITLE, info.getTitle(), dcSchema);
        extractDublinCoreListItems(metadata, TikaCoreProperties.CREATOR, info.getAuthor(), dcSchema);
        extractDublinCoreListItems(metadata, TikaCoreProperties.CONTRIBUTOR, null, dcSchema);
        addMetadata(metadata, TikaCoreProperties.CREATOR_TOOL, info.getCreator());
        addMetadata(metadata, TikaCoreProperties.KEYWORDS, info.getKeywords());
        addMetadata(metadata, "producer", info.getProducer());
        extractMultilingualItems(metadata, TikaCoreProperties.DESCRIPTION, null, dcSchema);

        // TODO: Move to description in Tika 2.0
        addMetadata(metadata, TikaCoreProperties.TRANSITION_SUBJECT_TO_OO_SUBJECT, info.getSubject());
        addMetadata(metadata, "trapped", info.getTrapped());
        try {
            // TODO Remove these in Tika 2.0
            addMetadata(metadata, "created", info.getCreationDate());
            addMetadata(metadata, TikaCoreProperties.CREATED, info.getCreationDate());
        } catch (IOException e) {
            // Invalid date format, just ignore
        }
        try {
            Calendar modified = info.getModificationDate();
            addMetadata(metadata, Metadata.LAST_MODIFIED, modified);
            addMetadata(metadata, TikaCoreProperties.MODIFIED, modified);
        } catch (IOException e) {
            // Invalid date format, just ignore
        }
        
        // All remaining metadata is custom
        // Copy this over as-is
        List<String> handledMetadata = Arrays.asList("Author", "Creator", "CreationDate", "ModDate",
                "Keywords", "Producer", "Subject", "Title", "Trapped");
        for(COSName key : info.getDictionary().keySet()) {
            String name = key.getName();
            if(! handledMetadata.contains(name)) {
        	addMetadata(metadata, name, info.getDictionary().getDictionaryObject(key));
            }
        }

        //try to get the various versions
        //Caveats:
        //    there is currently a fair amount of redundancy
        //    TikaCoreProperties.FORMAT can be multivalued
        //    There are also three potential pdf specific version keys: pdf:PDFVersion, pdfa:PDFVersion, pdf:PDFExtensionVersion        
        metadata.set("pdf:PDFVersion", Float.toString(document.getDocument().getVersion()));
        metadata.add(TikaCoreProperties.FORMAT.getName(), 
            MEDIA_TYPE.toString()+"; version="+
            Float.toString(document.getDocument().getVersion()));

        try {           
            if( xmp != null ) {
                xmp.addXMLNSMapping(XMPSchemaPDFAId.NAMESPACE, XMPSchemaPDFAId.class);
                XMPSchemaPDFAId pdfaxmp = (XMPSchemaPDFAId) xmp.getSchemaByClass(XMPSchemaPDFAId.class);
                if( pdfaxmp != null ) {
                    metadata.set("pdfaid:part", Integer.toString(pdfaxmp.getPart()));
                    if (pdfaxmp.getConformance() != null) {
                        metadata.set("pdfaid:conformance", pdfaxmp.getConformance());
                        String version = "A-"+pdfaxmp.getPart()+pdfaxmp.getConformance().toLowerCase(Locale.ROOT);
                        metadata.set("pdfa:PDFVersion", version );
                        metadata.add(TikaCoreProperties.FORMAT.getName(), 
                            MEDIA_TYPE.toString()+"; version=\""+version+"\"" );
                    }
                } 
                // TODO WARN if this XMP version is inconsistent with document header version?          
            }
        } catch (IOException e) {
            metadata.set(TikaCoreProperties.TIKA_META_PREFIX+"pdf:metadata-xmp-parse-failed", ""+e);
        }
        //TODO: Let's try to move this into PDFBox.
        //Attempt to determine Adobe extension level, if present:
        COSDictionary root = document.getDocumentCatalog().getCOSDictionary();
        COSDictionary extensions = (COSDictionary) root.getDictionaryObject(COSName.getPDFName("Extensions") );
        if( extensions != null ) {
            for( COSName extName : extensions.keySet() ) {
                // If it's an Adobe one, interpret it to determine the extension level:
                if( extName.equals( COSName.getPDFName("ADBE") )) {
                    COSDictionary adobeExt = (COSDictionary) extensions.getDictionaryObject(extName);
                    if( adobeExt != null ) {
                        String baseVersion = adobeExt.getNameAsString(COSName.getPDFName("BaseVersion"));
                        int el = adobeExt.getInt(COSName.getPDFName("ExtensionLevel"));
                        //-1 is sentinel value that something went wrong in getInt
                        if (el != -1) {
                            metadata.set("pdf:PDFExtensionVersion", baseVersion+" Adobe Extension Level "+el );
                            metadata.add(TikaCoreProperties.FORMAT.getName(), 
                                MEDIA_TYPE.toString()+"; version=\""+baseVersion+" Adobe Extension Level "+el+"\"");
                        }
                    }                   
                } else {
                    // WARN that there is an Extension, but it's not Adobe's, and so is a 'new' format'.
                    metadata.set("pdf:foundNonAdobeExtensionName", extName.getName());
                }
            }
        }
    }

   /**
     * Try to extract all multilingual items from the XMPSchema
     * <p>
     * This relies on the property having a valid xmp getName()
     * <p>
     * For now, this only extracts the first language if the property does not allow multiple values (see TIKA-1295)
     * @param metadata
     * @param property
     * @param pdfBoxBaseline
     * @param schema
     */
    private void extractMultilingualItems(Metadata metadata, Property property,
            String pdfBoxBaseline, XMPSchema schema) {
        //if schema is null, just go with pdfBoxBaseline
        if (schema == null) {
            if (pdfBoxBaseline != null && pdfBoxBaseline.length() > 0) {
                metadata.set(property, pdfBoxBaseline);
            }
            return;
        }

        for (String lang : schema.getLanguagePropertyLanguages(property.getName())) {
            String value = schema.getLanguageProperty(property.getName(), lang);

            if (value != null && value.length() > 0) {
                //if you're going to add it below in the baseline addition, don't add it now
                if (pdfBoxBaseline != null && value.equals(pdfBoxBaseline)){
                    continue;
                }
                metadata.add(property, value); 
                if (! property.isMultiValuePermitted()){
                    return;
                }
            }
        }

        if (pdfBoxBaseline != null && pdfBoxBaseline.length() > 0) {
            //if we've already added something above and multivalue is not permitted
            //return.
            if (! property.isMultiValuePermitted()){
                if (metadata.get(property) != null){
                    return;
                }
            }
            metadata.add(property,  pdfBoxBaseline);
        }
    }


    /**
     * This tries to read a list from a particular property in
     * XMPSchemaDublinCore.
     * If it can't find the information, it falls back to the 
     * pdfboxBaseline.  The pdfboxBaseline should be the value
     * that pdfbox returns from its PDDocumentInformation object
     * (e.g. getAuthor()) This method is designed include the pdfboxBaseline,
     * and it should not duplicate the pdfboxBaseline.
     * <p>
     * Until PDFBOX-1803/TIKA-1233 are fixed, do not call this
     * on dates!
     * <p>
     * This relies on the property having a DublinCore compliant getName()
     * 
     * @param property
     * @param pdfBoxBaseline
     * @param dc
     * @param metadata
     */
    private void extractDublinCoreListItems(Metadata metadata, Property property, 
            String pdfBoxBaseline, XMPSchemaDublinCore dc) {
        //if no dc, add baseline and return
        if (dc == null) {
            if (pdfBoxBaseline != null && pdfBoxBaseline.length() > 0) {
                addMetadata(metadata, property, pdfBoxBaseline);
            }
            return;
        }
        List<String> items = getXMPBagOrSeqList(dc, property.getName());
        if (items == null) {
            if (pdfBoxBaseline != null && pdfBoxBaseline.length() > 0) {
                addMetadata(metadata, property, pdfBoxBaseline);
            }
            return;
        }
        for (String item : items) {
            if (pdfBoxBaseline != null && ! item.equals(pdfBoxBaseline)) {
                addMetadata(metadata, property, item);
            }
        }
        //finally, add the baseline
        if (pdfBoxBaseline != null && pdfBoxBaseline.length() > 0) {
            addMetadata(metadata, property, pdfBoxBaseline);
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
    private List<String> getXMPBagOrSeqList(XMPSchema schema, String name) {
        List<String> ret = schema.getBagList(name);
        if (ret == null) {
            ret = schema.getSequenceList(name);
        }
        return ret;
    }

    private void addMetadata(Metadata metadata, Property property, String value) {
        if (value != null) {
            metadata.add(property, value);
        }
    }
    
    private void addMetadata(Metadata metadata, String name, String value) {
        if (value != null) {
            metadata.add(name, value);
        }
    }

    private void addMetadata(Metadata metadata, String name, Calendar value) {
        if (value != null) {
            metadata.set(name, value.getTime().toString());
        }
    }

    private void addMetadata(Metadata metadata, Property property, Calendar value) {
        if (value != null) {
            metadata.set(property, value.getTime());
        }
    }

    /**
     * Used when processing custom metadata entries, as PDFBox won't do
     *  the conversion for us in the way it does for the standard ones
     */
    private void addMetadata(Metadata metadata, String name, COSBase value) {
        if(value instanceof COSArray) {
            for(Object v : ((COSArray)value).toList()) {
                addMetadata(metadata, name, ((COSBase) v));
            }
        } else if(value instanceof COSString) {
            addMetadata(metadata, name, ((COSString)value).getString());
        }
        // Avoid calling COSDictionary#toString, since it can lead to infinite
        // recursion. See TIKA-1038 and PDFBOX-1835.
        else if (value != null && !(value instanceof COSDictionary)) {
            addMetadata(metadata, name, value.toString());
        }
    }

    public void setPDFParserConfig(PDFParserConfig config) {
        this.defaultConfig = config;
    }
    
    public PDFParserConfig getPDFParserConfig() {
        return defaultConfig;
    }
    
    /**
     * If true, the parser will use the NonSequentialParser.  This may
     * be faster than the full doc parser.
     * If false (default), this will use the full doc parser.
     * 
     * @deprecated use {@link #setPDFParserConfig(PDFParserConfig)}
     */
    public void setUseNonSequentialParser(boolean v) {
        defaultConfig.setUseNonSequentialParser(v);
    }
    
    /** 
     * @see #setUseNonSequentialParser(boolean) 
     * @deprecated use {@link #getPDFParserConfig()}
     */
    public boolean getUseNonSequentialParser() {
        return defaultConfig.getUseNonSequentialParser();
    }
    
    /**
     *  If true (the default), the parser should estimate
     *  where spaces should be inserted between words.  For
     *  many PDFs this is necessary as they do not include
     *  explicit whitespace characters.
     *
     *  @deprecated use {@link #setPDFParserConfig(PDFParserConfig)}
     */
    public void setEnableAutoSpace(boolean v) {
        defaultConfig.setEnableAutoSpace(v);
    }

    /** 
     * @see #setEnableAutoSpace(boolean) 
     * @deprecated use {@link #getPDFParserConfig()}
     */
    public boolean getEnableAutoSpace() {
        return defaultConfig.getEnableAutoSpace();
    }

    /**
     * If true (the default), text in annotations will be
     * extracted.
     * @deprecated use {@link #setPDFParserConfig(PDFParserConfig)}
     */
    public void setExtractAnnotationText(boolean v) {
        defaultConfig.setExtractAnnotationText(v);
    }

    /**
     * If true, text in annotations will be extracted.
     * 
     * @deprecated use {@link #getPDFParserConfig()}
     */
    public boolean getExtractAnnotationText() {
        return defaultConfig.getExtractAnnotationText();
    }

    /**
     *  If true, the parser should try to remove duplicated
     *  text over the same region.  This is needed for some
     *  PDFs that achieve bolding by re-writing the same
     *  text in the same area.  Note that this can
     *  slow down extraction substantially (PDFBOX-956) and
     *  sometimes remove characters that were not in fact
     *  duplicated (PDFBOX-1155).  By default this is disabled.
     *  
     *  @deprecated use {@link #setPDFParserConfig(PDFParserConfig)}
     */
    public void setSuppressDuplicateOverlappingText(boolean v) {
        defaultConfig.setSuppressDuplicateOverlappingText(v);
    }

    /** 
     * @see #setSuppressDuplicateOverlappingText(boolean) 
     * 
     * @deprecated use {@link #getPDFParserConfig()}
     */
    public boolean getSuppressDuplicateOverlappingText() {
        return defaultConfig.getSuppressDuplicateOverlappingText();
    }

    /**
     *  If true, sort text tokens by their x/y position
     *  before extracting text.  This may be necessary for
     *  some PDFs (if the text tokens are not rendered "in
     *  order"), while for other PDFs it can produce the
     *  wrong result (for example if there are 2 columns,
     *  the text will be interleaved).  Default is false.
     *  
     *  @deprecated use {@link #setPDFParserConfig(PDFParserConfig)}
     */
    public void setSortByPosition(boolean v) {
        defaultConfig.setSortByPosition(v);
    }

    /** 
     * @see #setSortByPosition(boolean) 
     * 
     * @deprecated use {@link #getPDFParserConfig()}
     */
    public boolean getSortByPosition() {
        return defaultConfig.getSortByPosition();
    }

}
