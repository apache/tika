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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;


/**
 * A detector that works on a Zip document
 *  to figure out exactly what the file is
 */
public class ZipContainerDetector implements Detector {
    public MediaType detect(InputStream input, Metadata metadata)
             throws IOException {
        ZipInputStream zip = new ZipInputStream(input);
        ZipEntry entry = zip.getNextEntry();
        while (entry != null) {
            // Is it an Open Document file?
            if (entry.getName().equals("mimetype")) {
                String type = IOUtils.toString(zip, "UTF-8");
                int splitAt = type.indexOf('/');
                if(splitAt > -1) {
                    return new MediaType(
                	    type.substring(0,splitAt), 
                	    type.substring(splitAt+1)
                    );
                }
                return MediaType.APPLICATION_ZIP;
            } else if (entry.getName().equals("[Content_Types].xml")) {
                // Office Open XML
        	// TODO
            }
            entry = zip.getNextEntry();
        }
        
        return MediaType.APPLICATION_ZIP;
    }
}

