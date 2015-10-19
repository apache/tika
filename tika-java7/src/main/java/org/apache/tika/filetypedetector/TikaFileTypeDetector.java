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

package org.apache.tika.filetypedetector;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;

import org.apache.tika.Tika;
import org.apache.tika.mime.MimeTypes;

public class TikaFileTypeDetector extends FileTypeDetector {
    private final Tika tika = new Tika();
    
    public TikaFileTypeDetector() {
        super();
    }
    
    @Override
    public String probeContentType(Path path) throws IOException {
        // Try to detect based on the file name only for efficiency
        String fileNameDetect = tika.detect(path.toString());
        if(!fileNameDetect.equals(MimeTypes.OCTET_STREAM)) {
            return fileNameDetect;
        }
        
        // Then check the file content if necessary
        String fileContentDetect = tika.detect(path);
        if(!fileContentDetect.equals(MimeTypes.OCTET_STREAM)) {
            return fileContentDetect;
        }
        
        // Specification says to return null if we could not 
        // conclusively determine the file type
        return null;
    }
    
}
