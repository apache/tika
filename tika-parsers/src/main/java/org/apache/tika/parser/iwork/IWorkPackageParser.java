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
package org.apache.tika.parser.iwork;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.tika.detect.XmlRootExtractor;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A parser for the IWork container files. This includes *.key, *.pages and *.numbers files.
 * This parser delegates the relevant entries to a {@link ContentHandler} that parsers the content.
 * 
 * Currently supported formats:
 * <ol>
 * <li>Keynote format version 2.x. Currently only tested with Keynote version 5.x
 * <li>Pages format version 1.x. Currently only tested with Pages version 4.0.x
 * <li>Numbers format version 1.x. Currently only tested with Numbers version 2.0.x
 * </ol>
 */
public class IWorkPackageParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = -2160322853809682372L;

    /**
     * Which files within an iWork file contain the actual content?
     */
    public final static Set<String> IWORK_CONTENT_ENTRIES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("index.apxl", "index.xml", "presentation.apxl"))
    );
    /**
     * All iWork files contain one of these, so we can detect based on it
     */
    public final static String IWORK_COMMON_ENTRY = "buildVersionHistory.plist";
    
    public enum IWORKDocumentType {
       KEYNOTE("http://developer.apple.com/namespaces/keynote2", "presentation", MediaType.application("vnd.apple.keynote")),
       NUMBERS("http://developer.apple.com/namespaces/ls", "document", MediaType.application("vnd.apple.numbers")),
       PAGES("http://developer.apple.com/namespaces/sl", "document", MediaType.application("vnd.apple.pages")),
       ENCRYPTED(null, null, MediaType.application("x-tika-iworks-protected"));
       
       private final String namespace;
       private final String part;
       private final MediaType type;
       
       IWORKDocumentType(String namespace, String part, MediaType type) {
          this.namespace = namespace;
          this.part = part;
          this.type = type;
       }
       
       public String getNamespace() {
          return namespace;
       }

       public String getPart() {
          return part;
       }

       public MediaType getType() {
          return type;
       }

       public static IWORKDocumentType detectType(ZipArchiveEntry entry, ZipFile zip) {
          try {
             if (entry == null) {
                 return null;
             }

             InputStream stream = zip.getInputStream(entry);
             try {
                return detectType(stream);
             } finally {
                 stream.close();
             }
          } catch (IOException e) {
             return null;
          }
       }
       
       public static IWORKDocumentType detectType(ZipArchiveEntry entry, ZipArchiveInputStream zip) {
          if (entry == null) {
              return null;
          }

          return detectType(zip);
       }
       
       private static IWORKDocumentType detectType(InputStream stream) {
          QName qname = new XmlRootExtractor().extractRootElement(stream);
          if (qname != null) {
             String uri = qname.getNamespaceURI();
             String local = qname.getLocalPart();
            
             for (IWORKDocumentType type : values()) {
                if(type.getNamespace().equals(uri) && 
                   type.getPart().equals(local)) {
                   return type;
                }
             }
          } else {
             // There was a problem with extracting the root type
             // Password Protected iWorks files are funny, but we can usually
             //  spot them because they encrypt part of the zip stream 
             try {
                stream.read();
             } catch(UnsupportedZipFeatureException e) {
                // Compression field was likely encrypted
                return ENCRYPTED;
             } catch(Exception ignored) {
             }
          }
          return null;
       }
    }

    /**
     * This parser handles all iWorks formats.
     */
    private final static Set<MediaType> supportedTypes =
         Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                MediaType.application("vnd.apple.iwork"),
                IWORKDocumentType.KEYNOTE.getType(),
                IWORKDocumentType.NUMBERS.getType(),
                IWORKDocumentType.PAGES.getType()
         )));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return supportedTypes;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        ZipArchiveInputStream zip = new ZipArchiveInputStream(stream);
        ZipArchiveEntry entry = zip.getNextZipEntry();

        while (entry != null) {
            if (!IWORK_CONTENT_ENTRIES.contains(entry.getName())) {
                entry = zip.getNextZipEntry();
                continue;
            }

            InputStream entryStream = new BufferedInputStream(zip, 4096);
            entryStream.mark(4096);
            IWORKDocumentType type = IWORKDocumentType.detectType(entryStream);
            entryStream.reset();
            
            if(type != null) {
               XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
               ContentHandler contentHandler;
               
               switch(type) {
               case KEYNOTE:
                  contentHandler = new KeynoteContentHandler(xhtml, metadata);
                  break;
               case NUMBERS:
                  contentHandler = new NumbersContentHandler(xhtml, metadata);
                  break;
               case PAGES:
                  contentHandler = new PagesContentHandler(xhtml, metadata);
                  break;
               case ENCRYPTED:
                   // We can't do anything for the file right now
                   contentHandler = null;
                   break;
               default:
                  throw new TikaException("Unhandled iWorks file " + type);
               }

               metadata.add(Metadata.CONTENT_TYPE, type.getType().toString());
               xhtml.startDocument();
               if (contentHandler != null) {
                  context.getSAXParser().parse(
                          new CloseShieldInputStream(entryStream),
                          new OfflineContentHandler(contentHandler)
                  );
               }
               xhtml.endDocument();
            }
            
            entry = zip.getNextZipEntry();
        }
        zip.close();
    }

}
