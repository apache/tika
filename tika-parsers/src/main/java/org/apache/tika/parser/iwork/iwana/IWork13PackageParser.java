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

package org.apache.tika.parser.iwork.iwana;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class IWork13PackageParser extends AbstractParser {

    public enum IWork13DocumentType {
        KEYNOTE13(MediaType.application("vnd.apple.keynote.13")),
        NUMBERS13(MediaType.application("vnd.apple.numbers.13")),
        PAGES13(MediaType.application("vnd.apple.pages.13")),
        UNKNOWN13(MediaType.application("vnd.apple.unknown.13"));

        private final MediaType mediaType;

        IWork13DocumentType(MediaType mediaType) {
            this.mediaType = mediaType;
        }

        public MediaType getType() {
            return mediaType;
        }

        public static MediaType detect(ZipFile zipFile) {
           MediaType type = null;
           Enumeration<? extends ZipEntry> entries = zipFile.getEntries();
           while (entries.hasMoreElements()) {
              ZipEntry entry = entries.nextElement();
              type = IWork13DocumentType.detectIfPossible(entry);
              if (type != null) return type;
           }
           
           // If we get here, we don't know what it is
           return UNKNOWN13.getType();
        }
        
        /**
         * @return Specific type if this identifies one, otherwise null
         */
        protected static MediaType detectIfPossible(ZipEntry entry) {
           String name = entry.getName();
           if (! name.endsWith(".iwa")) return null;

           // Is it a uniquely identifying filename?
           if (name.equals("Index/MasterSlide.iwa") ||
               name.startsWith("Index/MasterSlide-")) {
              return KEYNOTE13.getType();
           }
           if (name.equals("Index/Slide.iwa") ||
               name.startsWith("Index/Slide-")) {
              return KEYNOTE13.getType();
           }
           
           // Is it the main document?
           if (name.equals("Index/Document.iwa")) {
              // TODO Decode the snappy stream, and check for the Message Type
              // =     2 (TN::SheetArchive), it is a numbers file; 
              // = 10000 (TP::DocumentArchive), that's a pages file
           }

           // Unknown
           return null;
        }
    }

    /**
     * All iWork 13 files contain this, so we can detect based on it
     */
    public final static String IWORK13_COMMON_ENTRY = "Metadata/BuildVersionHistory.plist";

    private final static Set<MediaType> supportedTypes = Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
            IWork13DocumentType.KEYNOTE13.getType(),
            IWork13DocumentType.NUMBERS13.getType(),
            IWork13DocumentType.PAGES13.getType()
            )));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return supportedTypes;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
       // Open the Zip stream
       // Use a File if we can, and an already open zip is even better
       ZipFile zipFile = null;
       ZipInputStream zipStream = null;
       if (stream instanceof TikaInputStream) {
          TikaInputStream tis = (TikaInputStream) stream;
          Object container = ((TikaInputStream) stream).getOpenContainer();
          if (container instanceof ZipFile) {
             zipFile = (ZipFile) container;
          } else if (tis.hasFile()) {
             zipFile = new ZipFile(tis.getFile());
          } else {
             zipStream = new ZipInputStream(stream);
          }
       } else {
          zipStream = new ZipInputStream(stream);
       }

       // For now, just detect
       MediaType type = null;
       if (zipFile != null) {
          Enumeration<? extends ZipEntry> entries = zipFile.getEntries();
          while (entries.hasMoreElements()) {
             ZipEntry entry = entries.nextElement();
             if (type == null) {
                type = IWork13DocumentType.detectIfPossible(entry);
             }
          }
       } else {
          ZipEntry entry = zipStream.getNextEntry();
          while (entry != null) {
             if (type == null) {
                type = IWork13DocumentType.detectIfPossible(entry);
             }
             entry = zipStream.getNextEntry();
          }
       }
       if (type != null) {
          metadata.add(Metadata.CONTENT_TYPE, type.toString());
       }
    }
}
