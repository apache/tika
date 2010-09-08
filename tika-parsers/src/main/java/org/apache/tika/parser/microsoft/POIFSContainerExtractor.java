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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.poifs.storage.HeaderBlockConstants;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.LittleEndian;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.AutoContainerExtractor;
import org.apache.tika.extractor.ContainerEmbededResourceHandler;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.parser.microsoft.OfficeParser.POIFSDocumentType;

/**
 * A Container Extractor that handles extracting resources embeded
 *  in Microsoft office files, eg images and excel files embeded
 *  in a word document.
 */
public class POIFSContainerExtractor implements ContainerExtractor {
   private static final long serialVersionUID = 223856361982352348L;
   
   public boolean isSupported(TikaInputStream input) throws IOException {
      // Grab the first 8 bytes, used to do container detection
      input.mark(8);
      byte[] first8 = new byte[8];
      IOUtils.readFully(input, first8);
      input.reset();
      
      // Is it one of ours?
      long ole2Signature = LittleEndian.getLong(first8, 0);
      if(ole2Signature == HeaderBlockConstants._signature) {
         return true;
      }
      return false;
   }

   public void extract(TikaInputStream stream,
         ContainerExtractor recurseExtractor,
         ContainerEmbededResourceHandler handler) throws IOException,
         TikaException {
      POIFSFileSystem fs = new POIFSFileSystem(stream);
      extract(fs.getRoot(), recurseExtractor, handler);
   }
      
   public void extract(DirectoryEntry dir,
         ContainerExtractor recurseExtractor,
         ContainerEmbededResourceHandler handler) throws IOException,
         TikaException {
      // What kind of thing is it?
      POIFSDocumentType type = POIFSDocumentType.detectType(dir);
      switch(type) {
         case WORKBOOK:
            // Firstly do any embeded office documents
            for(Entry entry : dir) {
               if(entry.getName().startsWith("MBD")) {
                  handleEmbededOfficeDoc((DirectoryEntry)entry, recurseExtractor, handler);
               }
            }
            
            // Now do the embeded images
            // TODO
            break;
         case WORDDOCUMENT:
            // Firstly do any embeded office documents
            try {
               DirectoryEntry op = (DirectoryEntry)dir.getEntry("ObjectPool");
               for(Entry entry : op) {
                  if(entry.getName().startsWith("_")) {
                     handleEmbededOfficeDoc((DirectoryEntry)entry, recurseExtractor, handler);
                  }
               }
            } catch(FileNotFoundException e) {}
            
            // Now do the embeded images
            // TODO
            break;
         case POWERPOINT:
            // TODO
            break;
         case VISIO:
            // TODO
            break;
         case OUTLOOK:
            // Firstly do any embeded emails
            
            // Now any embeded files
            break;
      }
   }

   /**
    * Handle an office document that's embeded at the POIFS level
    */
   protected void handleEmbededOfficeDoc(DirectoryEntry dir,
         ContainerExtractor recurseExtractor,
         ContainerEmbededResourceHandler handler) throws IOException,
         TikaException {
      // Is it an embeded ooxml file?
      try {
         Entry ooxml = dir.getEntry("Package");
         // TODO
      } catch(FileNotFoundException e) {}
      
      // Looks to be an embeded OLE2 office file
      
      // Need to dump the directory out to a new temp file, so
      //  it's stand along
      POIFSFileSystem newFS = new POIFSFileSystem();
      copy(dir, newFS.getRoot());
      
      File tmpFile = File.createTempFile("tika", ".ole2");
      FileOutputStream out = new FileOutputStream(tmpFile);
      newFS.writeFilesystem(out);
      out.close();
      
      // What kind of document is it?
      POIFSDocumentType type = POIFSDocumentType.detectType(dir);
      
      // Trigger for the document itself 
      TikaInputStream embeded = TikaInputStream.get(tmpFile);
      handler.handle(null, type.getType(), embeded);
      
      // If we are recursing, process the document's contents too
      if(recurseExtractor != null) {
         if(recurseExtractor instanceof POIFSContainerExtractor ||
            recurseExtractor instanceof AutoContainerExtractor) {
            // Shortcut - use already open poifs
            extract(dir, recurseExtractor, handler);
         } else {
            // Long way round, need to use the temporary document
            recurseExtractor.extract(embeded, recurseExtractor, handler);
         }
      }
      
      // Tidy up
      embeded.close();
      tmpFile.delete();
   }
   protected void copy(DirectoryEntry sourceDir, DirectoryEntry destDir) throws IOException {
      for(Entry entry : sourceDir) {
         if(entry instanceof DirectoryEntry) {
            // Need to recurse
            DirectoryEntry newDir = destDir.createDirectory(entry.getName());
            copy( (DirectoryEntry)entry, newDir );
         } else {
            // Copy entry
            InputStream contents = new DocumentInputStream( (DocumentEntry)entry ); 
            destDir.createDocument(entry.getName(), contents);
         }
      }
   }
}
