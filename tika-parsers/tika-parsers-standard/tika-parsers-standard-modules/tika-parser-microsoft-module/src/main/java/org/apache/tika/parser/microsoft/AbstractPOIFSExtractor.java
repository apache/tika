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
package org.apache.tika.parser.microsoft;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.poi.hpsf.ClassID;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.Ole10NativeException;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.StringUtil;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.zip.DefaultZipContainerDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.microsoft.OfficeParser.POIFSDocumentType;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;

abstract class AbstractPOIFSExtractor {

    private static final String OCX_NAME = "\u0003OCXNAME";
    protected final Metadata parentMetadata;//metadata of the parent/container document
    protected final OfficeParserConfig officeParserConfig;
    protected final ParseContext context;
    private final EmbeddedDocumentUtil embeddedDocumentUtil;
    private PasswordProvider passwordProvider;

    protected AbstractPOIFSExtractor(ParseContext context) {
        this(context, null);
    }

    protected AbstractPOIFSExtractor(ParseContext context, Metadata parentMetadata) {
        embeddedDocumentUtil = new EmbeddedDocumentUtil(context);

        this.passwordProvider = context.get(PasswordProvider.class);
        this.officeParserConfig = context.get(OfficeParserConfig.class, new OfficeParserConfig());
        this.parentMetadata = parentMetadata;
        this.context = context;
    }

    // Note - these cache, but avoid creating the default TikaConfig if not needed
    protected TikaConfig getTikaConfig() {
        return embeddedDocumentUtil.getTikaConfig();
    }

    protected Detector getDetector() {
        return embeddedDocumentUtil.getDetector();
    }

    /**
     * Returns the password to be used for this file, or null
     * if no / default password should be used
     */
    protected String getPassword() {
        if (passwordProvider != null) {
            return passwordProvider.getPassword(parentMetadata);
        }
        return null;
    }

    protected void handleEmbeddedResource(TikaInputStream resource, String filename,
                                          String relationshipID, String mediaType,
                                          XHTMLContentHandler xhtml, boolean outputHtml)
            throws IOException, SAXException, TikaException {
        handleEmbeddedResource(resource, filename, relationshipID, null, mediaType, xhtml,
                outputHtml);
    }

    protected void handleEmbeddedResource(TikaInputStream resource, String filename,
                                          String relationshipID, ClassID storageClassID,
                                          String mediaType, XHTMLContentHandler xhtml,
                                          boolean outputHtml)
            throws IOException, SAXException, TikaException {
        handleEmbeddedResource(resource, new Metadata(), filename, relationshipID, storageClassID,
                mediaType, xhtml, outputHtml);
    }

    protected void handleEmbeddedResource(TikaInputStream resource, Metadata embeddedMetadata,
                                          String filename, String relationshipID,
                                          ClassID storageClassID, String mediaType,
                                          XHTMLContentHandler xhtml, boolean outputHtml)
            throws IOException, SAXException, TikaException {

        try {
            if (filename != null) {
                embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            }
            if (relationshipID != null) {
                embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID, relationshipID);
            }
            if (storageClassID != null) {
                embeddedMetadata.set(TikaCoreProperties.EMBEDDED_STORAGE_CLASS_ID,
                        storageClassID.toString());
            }
            if (mediaType != null) {
                embeddedMetadata.set(Metadata.CONTENT_TYPE, mediaType);
            }

            if (embeddedDocumentUtil.shouldParseEmbedded(embeddedMetadata)) {
                embeddedDocumentUtil.parseEmbedded(resource, xhtml, embeddedMetadata, outputHtml);
            }
        } finally {
            resource.close();
        }
    }

    /**
     * Handle an office document that's embedded at the POIFS level
     */
    protected void handleEmbeddedOfficeDoc(DirectoryEntry dir, XHTMLContentHandler xhtml,
                                           boolean outputHtml)
            throws IOException, SAXException, TikaException {
        handleEmbeddedOfficeDoc(dir, null, xhtml, outputHtml);
    }

    /**
     * Handle an office document that's embedded at the POIFS level
     */
    protected void handleEmbeddedOfficeDoc(DirectoryEntry dir, String resourceName,
                                           XHTMLContentHandler xhtml, boolean outputHtml)
            throws IOException, SAXException, TikaException {


        // Is it an embedded OLE2 document, or an embedded OOXML document?
        //first try for ooxml
        Entry ooxml = dir.hasEntry("Package") ? dir.getEntry("Package") :
                (dir.hasEntry("package") ? dir.getEntry("package") : null);

        if (ooxml != null) {
            // It's OOXML (has a ZipFile):
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_LENGTH,
                    Integer.toString(((DocumentEntry)ooxml).getSize()));
            try (TikaInputStream stream = TikaInputStream
                    .get(new DocumentInputStream((DocumentEntry) ooxml))) {

                Detector detector = new DefaultZipContainerDetector();
                MediaType type = null;
                try {
                    type = detector.detect(stream, metadata);
                } catch (SecurityException e) {
                    throw e;
                } catch (Exception e) {
                    //if there's a stream error while detecting, give up
                    EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
                    return;
                }
                handleEmbeddedResource(stream, metadata,null, dir.getName(), dir.getStorageClsid(),
                        type.toString(), xhtml, outputHtml);
                return;
            }
        }

        // It's regular OLE2:

        // What kind of document is it?
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID, dir.getName());
        if (dir.getStorageClsid() != null) {
            metadata.set(TikaCoreProperties.EMBEDDED_STORAGE_CLASS_ID,
                    dir.getStorageClsid().toString());
        }
        POIFSDocumentType type = POIFSDocumentType.detectType(dir);
        String rName = (resourceName == null) ? dir.getName() : resourceName;
        extractOCXName(dir, metadata);
        if (type == POIFSDocumentType.OLE10_NATIVE) {
            handleOLENative(dir, type, rName, metadata, xhtml, outputHtml);
        } else if (type == POIFSDocumentType.COMP_OBJ) {
            handleCompObj(dir, type, rName, metadata, xhtml, outputHtml);
        } else if (type == POIFSDocumentType.OUTLOOK) {
            //for Outlook try to use the title first so that we don't wind up with __substg1.0_37...
            //if that doesn't exist, backoff to rName
            //add the suffix
            metadata.set(Metadata.CONTENT_TYPE, type.getType().toString());
            String name = tryToGetMsgTitle(dir, rName);
            if (! StringUtils.isBlank(name)) {
                if (StringUtils.isBlank(type.getExtension())) {
                    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
                } else {
                    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                            name + '.' + type.getExtension());
                }
            }
            parseEmbedded(dir, xhtml, metadata, outputHtml);
        } else {
            metadata.set(Metadata.CONTENT_TYPE, type.getType().toString());
            if (! StringUtils.isBlank(rName)) {
                if (StringUtils.isBlank(type.getExtension())) {
                    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, rName);
                } else {
                    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                            rName + '.' + type.getExtension());
                }
            }
            parseEmbedded(dir, xhtml, metadata, outputHtml);
        }
    }

    private void extractOCXName(DirectoryEntry dir, Metadata metadata) {
        if (! dir.hasEntry(OCX_NAME)) {
            return;
        }
        try {
            Entry e = dir.getEntry(OCX_NAME);
            if (!e.isDocumentEntry()) {
                return;
            }
            UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get();
            try (DocumentInputStream dis = new DocumentInputStream((DocumentEntry) e)) {
                IOUtils.copy(dis, bos);
            }
            byte[] bytes = bos.toByteArray();
            int charCount = (bytes.length - 4);
            if (charCount < 0) {
                return;
            }
            if (charCount % 2 != 0) {
                return;
            }
            charCount /= 2;
            String ocxName = StringUtil.getFromUnicodeLE0Terminated(bytes, 0, charCount);
            metadata.set(Office.OCX_NAME, ocxName);
        } catch (IOException e) {
            //log this?
        }
    }

    private void handleCompObj(DirectoryEntry dir, POIFSDocumentType type, String rName,
                               Metadata metadata, XHTMLContentHandler xhtml, boolean outputHtml)
            throws IOException, SAXException {
        //TODO: figure out if the equivalent of OLE 1.0's
        //getCommand() and getFileName() exist for OLE 2.0 to populate
        //TikaCoreProperties.ORIGINAL_RESOURCE_NAME

        String contentsEntryName = getContentsEntryName(dir);
        if (contentsEntryName == null) {
            //log or record exception?
            return;
        }
        // Grab the contents and process
        DocumentEntry contentsEntry;

        try {
            contentsEntry = (DocumentEntry) dir.getEntry(contentsEntryName);
        } catch (FileNotFoundException fnfe) {
            EmbeddedDocumentUtil.recordEmbeddedStreamException(fnfe, parentMetadata);
            return;
        }

        int length = contentsEntry.getSize();
        DocumentInputStream inp = null;
        try {
            inp = new DocumentInputStream(contentsEntry);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
            return;
        }
        try (TikaInputStream tis = TikaInputStream.get(inp)) {
            // Try to work out what it is
            MediaType mediaType = getDetector().detect(tis, metadata);
            String extension = type.getExtension();
            try {
                MimeType mimeType =
                        embeddedDocumentUtil.getMimeTypes().forName(mediaType.toString());
                extension = mimeType.getExtension();
            } catch (MimeTypeException mte) {
                // No details on this type are known
            }

            // Record what we can do about it
            metadata.set(Metadata.CONTENT_TYPE, mediaType.getType());
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, rName + extension);
            metadata.set(Metadata.CONTENT_LENGTH, Integer.toString(length));
            parseEmbedded(dir, tis, xhtml, metadata, outputHtml);
        } finally {
            inp.close();
        }
    }

    private String getContentsEntryName(DirectoryEntry dir) {
        /*
        if (dir.hasEntry("CorelDRAW")) {
            contentsEntry = (DocumentEntry) dir.getEntry("CorelDRAW");}
         */
        //TODO: modify getEntry to case insensitive when available in POI
        if (dir.hasEntry("CONTENTS")) {
            return "CONTENTS";
        } else if (dir.hasEntry("Contents")) {
            return "Contents";
        } else {
            for (String n : dir.getEntryNames()) {
                if ("contents".equalsIgnoreCase(n)) {
                    return n;
                }
            }
        }
        return null;
    }


    private void handleOLENative(DirectoryEntry dir, POIFSDocumentType type, String rName,
                                 Metadata metadata, XHTMLContentHandler xhtml, boolean outputHtml)
            throws IOException, SAXException {
        byte[] data = null;
        try {
            // Try to un-wrap the OLE10Native record:
            Ole10Native ole = Ole10Native.createFromEmbeddedOleObject((DirectoryNode) dir);
            if (ole.getLabel() != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, rName + '/' + ole.getLabel());
            } else {
                metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, rName);
            }
            if (ole.getCommand() != null) {
                metadata.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, ole.getCommand());
            }
            if (ole.getFileName() != null) {
                metadata.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, ole.getFileName());
            }
            data = ole.getDataBuffer();
            metadata.set(Metadata.CONTENT_LENGTH, Integer.toString(data.length));
        } catch (Ole10NativeException ex) {
            // Not a valid OLE10Native record, skip it
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
            return;
        }
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            parseEmbedded(dir, tis, xhtml, metadata, outputHtml);
        }
    }

    private void parseEmbedded(DirectoryEntry dir, TikaInputStream tis, XHTMLContentHandler xhtml,
                               Metadata metadata, boolean outputHtml) throws IOException,
            SAXException {
        if (!embeddedDocumentUtil.shouldParseEmbedded(metadata)) {
            return;
        }
        if (dir.getStorageClsid() != null) {
            metadata.set(TikaCoreProperties.EMBEDDED_STORAGE_CLASS_ID,
                    dir.getStorageClsid().toString());
        }
        embeddedDocumentUtil.parseEmbedded(tis, xhtml, metadata, outputHtml);
    }

    private void parseEmbedded(DirectoryEntry dir, XHTMLContentHandler xhtml, Metadata metadata,
                               boolean outputHtml)
            throws IOException, SAXException {
        if (!embeddedDocumentUtil.shouldParseEmbedded(metadata)) {
            return;
        }
        try (TikaInputStream tis = TikaInputStream.get(new byte[0])) {
            tis.setOpenContainer(dir);
            if (dir.getStorageClsid() != null) {
                metadata.set(TikaCoreProperties.EMBEDDED_STORAGE_CLASS_ID,
                        dir.getStorageClsid().toString());
            }
            embeddedDocumentUtil.parseEmbedded(tis, xhtml, metadata, outputHtml);
        }
    }


    public static String tryToGetMsgTitle(DirectoryEntry node, String defaultVal) {

        for (String entryName : new String[] {"__substg1.0_0037001F", "__substg1.0_0E1D001F", "__substg1.0_0070001F"} ) {
            try {
                Entry entry = node.getEntry(entryName);
                if (entry instanceof DocumentEntry) {
                    try (InputStream is = new BoundedInputStream(1000, new DocumentInputStream((DocumentEntry) entry))) {
                        return org.apache.commons.io.IOUtils.toString(is, StandardCharsets.UTF_16LE);
                    }
                }
            } catch (IOException e) {
                //do nothing
            }
        }
        return defaultVal;
    }
}
