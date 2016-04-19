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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.Ole10NativeException;
import org.apache.poi.hpsf.ClassID;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.microsoft.OfficeParser.POIFSDocumentType;
import org.apache.tika.parser.pkg.ZipContainerDetector;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

abstract class AbstractPOIFSExtractor {
    private static final Log logger = LogFactory.getLog(AbstractPOIFSExtractor.class);
    private final EmbeddedDocumentExtractor extractor;
    private PasswordProvider passwordProvider;
    private TikaConfig tikaConfig;
    private MimeTypes mimeTypes;
    private Detector detector;
    private Metadata metadata;

    protected AbstractPOIFSExtractor(ParseContext context) {
        this(context, null);
    }

    protected AbstractPOIFSExtractor(ParseContext context, Metadata metadata) {
        EmbeddedDocumentExtractor ex = context.get(EmbeddedDocumentExtractor.class);

        if (ex == null) {
            this.extractor = new ParsingEmbeddedDocumentExtractor(context);
        } else {
            this.extractor = ex;
        }

        this.passwordProvider = context.get(PasswordProvider.class);
        this.tikaConfig = context.get(TikaConfig.class);
        this.mimeTypes = context.get(MimeTypes.class);
        this.detector = context.get(Detector.class);
        this.metadata = metadata;
    }

    // Note - these cache, but avoid creating the default TikaConfig if not needed
    protected TikaConfig getTikaConfig() {
        if (tikaConfig == null) {
            tikaConfig = TikaConfig.getDefaultConfig();
        }
        return tikaConfig;
    }

    protected Detector getDetector() {
        if (detector != null) return detector;

        detector = getTikaConfig().getDetector();
        return detector;
    }

    protected MimeTypes getMimeTypes() {
        if (mimeTypes != null) return mimeTypes;

        mimeTypes = getTikaConfig().getMimeRepository();
        return mimeTypes;
    }

    /**
     * Returns the password to be used for this file, or null
     * if no / default password should be used
     */
    protected String getPassword() {
        if (passwordProvider != null) {
            return passwordProvider.getPassword(metadata);
        }
        return null;
    }

    protected void handleEmbeddedResource(TikaInputStream resource, String filename,
                                          String relationshipID, String mediaType, XHTMLContentHandler xhtml,
                                          boolean outputHtml)
            throws IOException, SAXException, TikaException {
        handleEmbeddedResource(resource, filename, relationshipID, null, mediaType, xhtml, outputHtml);
    }

    protected void handleEmbeddedResource(TikaInputStream resource, String filename,
                                          String relationshipID, ClassID storageClassID, String mediaType, XHTMLContentHandler xhtml,
                                          boolean outputHtml)
            throws IOException, SAXException, TikaException {
        try {
            Metadata metadata = new Metadata();
            if (filename != null) {
                metadata.set(Metadata.TIKA_MIME_FILE, filename);
                metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
            }
            if (relationshipID != null) {
                metadata.set(Metadata.EMBEDDED_RELATIONSHIP_ID, relationshipID);
            }
            if (storageClassID != null) {
                metadata.set(Metadata.EMBEDDED_STORAGE_CLASS_ID, storageClassID.toString());
            }
            if (mediaType != null) {
                metadata.set(Metadata.CONTENT_TYPE, mediaType);
            }

            if (extractor.shouldParseEmbedded(metadata)) {
                extractor.parseEmbedded(resource, xhtml, metadata, outputHtml);
            }
        } finally {
            resource.close();
        }
    }

    /**
     * Handle an office document that's embedded at the POIFS level
     */
    protected void handleEmbeddedOfficeDoc(
            DirectoryEntry dir, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {

        // Is it an embedded OLE2 document, or an embedded OOXML document?

        if (dir.hasEntry("Package")) {
            // It's OOXML (has a ZipFile):
            Entry ooxml = dir.getEntry("Package");

            try (TikaInputStream stream = TikaInputStream.get(
                    new DocumentInputStream((DocumentEntry) ooxml))) {
                ZipContainerDetector detector = new ZipContainerDetector();
                MediaType type = detector.detect(stream, new Metadata());
                handleEmbeddedResource(stream, null, dir.getName(), dir.getStorageClsid(), type.toString(), xhtml, true);
                return;
            }
        }

        // It's regular OLE2:

        // What kind of document is it?
        Metadata metadata = new Metadata();
        metadata.set(Metadata.EMBEDDED_RELATIONSHIP_ID, dir.getName());
        if (dir.getStorageClsid() != null) {
            metadata.set(Metadata.EMBEDDED_STORAGE_CLASS_ID, dir.getStorageClsid().toString());
        }
        POIFSDocumentType type = POIFSDocumentType.detectType(dir);
        TikaInputStream embedded = null;

        try {
            if (type == POIFSDocumentType.OLE10_NATIVE) {
                try {
                    // Try to un-wrap the OLE10Native record:
                    Ole10Native ole = Ole10Native.createFromEmbeddedOleObject((DirectoryNode) dir);
                    if (ole.getLabel() != null) {
                        metadata.set(Metadata.RESOURCE_NAME_KEY, dir.getName() + '/' + ole.getLabel());
                    }
                    byte[] data = ole.getDataBuffer();
                    embedded = TikaInputStream.get(data);
                } catch (Ole10NativeException ex) {
                    // Not a valid OLE10Native record, skip it
                } catch (Exception e) {
                    logger.warn("Ignoring unexpected exception while parsing possible OLE10_NATIVE embedded document " + dir.getName(), e);
                }
            } else if (type == POIFSDocumentType.COMP_OBJ) {
                try {
                    // Grab the contents and process
                    DocumentEntry contentsEntry;
                    try {
                        contentsEntry = (DocumentEntry) dir.getEntry("CONTENTS");
                    } catch (FileNotFoundException ioe) {
                        contentsEntry = (DocumentEntry) dir.getEntry("Contents");
                    }
                    DocumentInputStream inp = new DocumentInputStream(contentsEntry);
                    byte[] contents = new byte[contentsEntry.getSize()];
                    inp.readFully(contents);
                    embedded = TikaInputStream.get(contents);

                    // Try to work out what it is
                    MediaType mediaType = getDetector().detect(embedded, new Metadata());
                    String extension = type.getExtension();
                    try {
                        MimeType mimeType = getMimeTypes().forName(mediaType.toString());
                        extension = mimeType.getExtension();
                    } catch (MimeTypeException mte) {
                        // No details on this type are known
                    }

                    // Record what we can do about it
                    metadata.set(Metadata.CONTENT_TYPE, mediaType.getType().toString());
                    metadata.set(Metadata.RESOURCE_NAME_KEY, dir.getName() + extension);
                } catch (Exception e) {
                    throw new TikaException("Invalid embedded resource", e);
                }
            } else {
                metadata.set(Metadata.CONTENT_TYPE, type.getType().toString());
                metadata.set(Metadata.RESOURCE_NAME_KEY, dir.getName() + '.' + type.getExtension());
            }

            // Should we parse it?
            if (extractor.shouldParseEmbedded(metadata)) {
                if (embedded == null) {
                    // Make a TikaInputStream that just
                    // passes the root directory of the
                    // embedded document, and is otherwise
                    // empty (byte[0]):
                    embedded = TikaInputStream.get(new byte[0]);
                    embedded.setOpenContainer(dir);
                }
                extractor.parseEmbedded(embedded, xhtml, metadata, true);
            }
        } finally {
            if (embedded != null) {
                embedded.close();
            }
        }
    }
}
