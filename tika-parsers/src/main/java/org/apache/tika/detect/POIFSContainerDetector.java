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

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.microsoft.OfficeParser.POIFSDocumentType;


/**
 * A detector that works on a POIFS OLE2 document
 *  to figure out exactly what the file is
 */
public class POIFSContainerDetector implements Detector {

    public MediaType detect(InputStream input, Metadata metadata)
             throws IOException {
        if (TikaInputStream.isTikaInputStream(input)) {
            TikaInputStream stream = TikaInputStream.get(input);

            // NOTE: POIFSFileSystem will close the FileInputStream
            POIFSFileSystem fs =
                new POIFSFileSystem(new FileInputStream(stream.getFile()));
            stream.setOpenContainer(fs);

            return POIFSDocumentType.detectType(fs).getType();
        } else {
            return MediaType.application("x-tika-msoffice");
        }
    }

}
