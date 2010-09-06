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

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

import org.apache.poi.poifs.common.POIFSConstants;
import org.apache.poi.poifs.storage.HeaderBlockConstants;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.LittleEndian;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;


/**
 * A detector that knows about the container formats that we support 
 *  (eg POIFS, Zip), and is able to peek inside them to better figure 
 *  out the contents.
 * Delegates to another {@link Detector} (normally {@link MimeTypes})
 *  to handle detection for non container formats. 
 * Should normally be used with a {@link TikaInputStream} to minimise 
 *  the memory usage.
 */
public class ContainerAwareDetector implements Detector {
    private Detector fallbackDetector;
    private ZipContainerDetector zipDetector;
    private POIFSContainerDetector poifsDetector;
    
    /**
     * Creates a new container detector, which will use the
     *  given detector for non container formats.
     * @param fallbackDetector The detector to use for non-containers
     */
    public ContainerAwareDetector(Detector fallbackDetector) {
        this.fallbackDetector = fallbackDetector;
        poifsDetector = new POIFSContainerDetector();
        zipDetector = new ZipContainerDetector();
    }
    
    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {
        return detect(TikaInputStream.get(input), metadata);
    }
    
    public MediaType detect(TikaInputStream input, Metadata metadata)
            throws IOException {
	
        // Grab the first 8 bytes, used to do container detection
        input.mark(8);
        byte[] first8 = new byte[8];
        IOUtils.readFully(input, first8);
        input.reset();
	
        // Is this a zip file?
        if(first8[0] == POIFSConstants.OOXML_FILE_HEADER[0] &&
           first8[1] == POIFSConstants.OOXML_FILE_HEADER[1] &&
           first8[2] == POIFSConstants.OOXML_FILE_HEADER[2] &&
           first8[3] == POIFSConstants.OOXML_FILE_HEADER[3]) {
        	try {
        	   return detect(input, metadata, zipDetector);
        	} catch (ZipException e) {
        		// Problem with the zip file, eg corrupt or truncated
            // Try the fallback in case there is enough data for that
            //  to be able to offer something useful
        		input = TikaInputStream.get(input.getFile());
        	}
        }
        
        // Is this an ole2 file?
        long ole2Signature = LittleEndian.getLong(first8, 0);
        if(ole2Signature == HeaderBlockConstants._signature) {
           try {
              return detect(input, metadata, poifsDetector);
           } catch(IOException e) {
              // Problem with the ole file, eg corrupt or truncated
              // Try the fallback in case there is enough data for that
              //  to be able to offer something useful
              input = TikaInputStream.get(input.getFile());
           }
        }
        
        // Add further container detection (eg tar.gz, ogg, avi) here
        
        // Not a supported container, ask our fall back
        //  detector to figure it out
        return fallbackDetector.detect(input, metadata);
    }
    
    /**
     * Does container-detector based detection, handling
     *  fallback in case of the default.
     */
    private MediaType detect(TikaInputStream input, Metadata metadata, 
               ContainerDetector detector) throws IOException {
       MediaType detected = detector.detect(input, metadata);
       MediaType defaultType = detector.getDefault(); 
       if(! detected.equals(defaultType)) {
          return detected;
       }
       
       // See if the fallback can do better
       detected = fallbackDetector.detect(input, metadata);
       if(! detected.equals(MediaType.OCTET_STREAM)) {
          return detected;
       } else {
          return defaultType;
       }
    }
}

