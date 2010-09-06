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
package org.apache.tika.detect;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.microsoft.OfficeParser.POIFSDocumentType;

/**
 * A detector that works on a POIFS OLE2 document
 *  to figure out exactly what the file is.
 * This should work for all OLE2 documents, whether
 *  they are ones supported by POI or not.
 */
public class POIFSContainerDetector implements ContainerDetector {
    private static final MediaType DEFAULT = MediaType.application("x-tika-msoffice");
    public MediaType getDefault() {
       return DEFAULT;
    }

    public MediaType detect(InputStream input, Metadata metadata)
             throws IOException {
        if (TikaInputStream.isTikaInputStream(input)) {
            TikaInputStream stream = TikaInputStream.get(input);
            return detect(stream, metadata);
        } else {
           // We can't do proper detection if we don't
           //  have a TikaInputStream
           return DEFAULT;
       }
    }

    public MediaType detect(TikaInputStream stream, Metadata metadata)
             throws IOException {
         // NOTE: POIFSFileSystem will close the FileInputStream
         POIFSFileSystem fs =
             new POIFSFileSystem(new FileInputStream(stream.getFile()));
         stream.setOpenContainer(fs);

         // See if it's one of the Microsoft Office file formats?
         POIFSDocumentType type = POIFSDocumentType.detectType(fs);
         if(type != POIFSDocumentType.UNKNOWN) {
            return type.getType();
         }
         
         // Is it one of the Corel formats which use OLE2?
         MediaType mt = detectCorel(fs.getRoot());
         if(mt != null) return mt;
         
         // We don't know, sorry
         return DEFAULT;
    }

    protected MediaType detectCorel(DirectoryNode directory) {
       boolean hasNativeContentMain = false;
       boolean hasPerfectOfficeMain = false;
       boolean hasPerfectOfficeObjects = false;
       boolean hasSlideShow = false;
       
       for(Entry entry : directory) {
          if(entry.getName().equals("NativeContent_MAIN")) {
             hasNativeContentMain = true;
          }
          if(entry.getName().equals("PerfectOffice_MAIN")) {
             hasPerfectOfficeMain = true;
          }
          if(entry.getName().equals("PerfectOffice_OBJECTS")) {
             hasPerfectOfficeObjects = true;
          }
          if(entry.getName().equals("SlideShow")) {
             hasSlideShow = true;
          }
       }
       
       if(hasPerfectOfficeMain && hasSlideShow) {
          return MediaType.application("x-corelpresentations"); // .shw
       }
       if(hasPerfectOfficeMain && hasPerfectOfficeObjects) {
          return MediaType.application("x-quattro-pro"); // .wb?
       }
       if(!hasPerfectOfficeMain && hasNativeContentMain) {
          return MediaType.application("x-quattro-pro"); // .qpw
       }
       
       // Don't know, sorry
       return null;
    }
}
