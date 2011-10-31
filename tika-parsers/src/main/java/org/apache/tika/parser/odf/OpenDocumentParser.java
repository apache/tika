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
package org.apache.tika.parser.odf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

//import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
//import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.EndDocumentShieldingContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * OpenOffice parser
 */
public class OpenDocumentParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = -6410276875438618287L;

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                MediaType.application("vnd.sun.xml.writer"),
                MediaType.application("vnd.oasis.opendocument.text"),
                MediaType.application("vnd.oasis.opendocument.graphics"),
                MediaType.application("vnd.oasis.opendocument.presentation"),
                MediaType.application("vnd.oasis.opendocument.spreadsheet"),
                MediaType.application("vnd.oasis.opendocument.chart"),
                MediaType.application("vnd.oasis.opendocument.image"),
                MediaType.application("vnd.oasis.opendocument.formula"),
                MediaType.application("vnd.oasis.opendocument.text-master"),
                MediaType.application("vnd.oasis.opendocument.text-web"),
                MediaType.application("vnd.oasis.opendocument.text-template"),
                MediaType.application("vnd.oasis.opendocument.graphics-template"),
                MediaType.application("vnd.oasis.opendocument.presentation-template"),
                MediaType.application("vnd.oasis.opendocument.spreadsheet-template"),
                MediaType.application("vnd.oasis.opendocument.chart-template"),
                MediaType.application("vnd.oasis.opendocument.image-template"),
                MediaType.application("vnd.oasis.opendocument.formula-template"),
                MediaType.application("x-vnd.oasis.opendocument.text"),
                MediaType.application("x-vnd.oasis.opendocument.graphics"),
                MediaType.application("x-vnd.oasis.opendocument.presentation"),
                MediaType.application("x-vnd.oasis.opendocument.spreadsheet"),
                MediaType.application("x-vnd.oasis.opendocument.chart"),
                MediaType.application("x-vnd.oasis.opendocument.image"),
                MediaType.application("x-vnd.oasis.opendocument.formula"),
                MediaType.application("x-vnd.oasis.opendocument.text-master"),
                MediaType.application("x-vnd.oasis.opendocument.text-web"),
                MediaType.application("x-vnd.oasis.opendocument.text-template"),
                MediaType.application("x-vnd.oasis.opendocument.graphics-template"),
                MediaType.application("x-vnd.oasis.opendocument.presentation-template"),
                MediaType.application("x-vnd.oasis.opendocument.spreadsheet-template"),
                MediaType.application("x-vnd.oasis.opendocument.chart-template"),
                MediaType.application("x-vnd.oasis.opendocument.image-template"),
                MediaType.application("x-vnd.oasis.opendocument.formula-template"))));

    private Parser meta = new OpenDocumentMetaParser();

    private Parser content = new OpenDocumentContentParser();

    public Parser getMetaParser() {
        return meta;
    }

    public void setMetaParser(Parser meta) {
        this.meta = meta;
    }

    public Parser getContentParser() {
        return content;
    }

    public void setContentParser(Parser content) {
        this.content = content;
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler baseHandler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // TODO: reuse the already opened ZIPFile, if
        // present

        /*
        ZipFile zipFile;
        if (stream instanceof TikaInputStream) {
            TikaInputStream tis = (TikaInputStream) stream;
            Object container = ((TikaInputStream) stream).getOpenContainer();
            if (container instanceof ZipFile) {
                zipFile = (ZipFile) container;
            } else if (tis.hasFile()) {
                zipFile = new ZipFile(tis.getFile());                
            }
        }
        */

        // TODO: if incoming IS is a TIS with a file
        // associated, we should open ZipFile so we can
        // visit metadata, mimetype first; today we lose
        // all the metadata if meta.xml is hit after
        // content.xml in the stream.  Then we can still
        // read-once for the content.xml.

        XHTMLContentHandler xhtml = new XHTMLContentHandler(baseHandler, metadata);

        // As we don't know which of the metadata or the content
        //  we'll hit first, catch the endDocument call initially
        EndDocumentShieldingContentHandler handler = 
          new EndDocumentShieldingContentHandler(xhtml);

        // Process the file in turn
        ZipInputStream zip = new ZipInputStream(stream);
        ZipEntry entry = zip.getNextEntry();
        while (entry != null) {
            if (entry.getName().equals("mimetype")) {
                String type = IOUtils.toString(zip, "UTF-8");
                metadata.set(Metadata.CONTENT_TYPE, type);
            } else if (entry.getName().equals("meta.xml")) {
                meta.parse(zip, new DefaultHandler(), metadata, context);
            } else if (entry.getName().endsWith("content.xml")) {
                if (content instanceof OpenDocumentContentParser) {
                    ((OpenDocumentContentParser) content).parseInternal(zip, handler, metadata, context);
                } else {
                    // Foreign content parser was set:
                    content.parse(zip, handler, metadata, context);
                }
            } else if (entry.getName().endsWith("styles.xml")) {
                if (content instanceof OpenDocumentContentParser) {
                    ((OpenDocumentContentParser) content).parseInternal(zip, handler, metadata, context);
                } else {
                    // Foreign content parser was set:
                    content.parse(zip, handler, metadata, context);
                }
            }
            entry = zip.getNextEntry();
        }
        
        // Only now call the end document
        if(handler.getEndDocumentWasCalled()) {
           handler.reallyEndDocument();
        }
    }

}
