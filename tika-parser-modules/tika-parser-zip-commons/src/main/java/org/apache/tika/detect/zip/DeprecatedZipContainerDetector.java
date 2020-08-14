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
package org.apache.tika.detect.zip;

//see TODO then delete this class

/**
 * A detector that works on Zip documents and tries to figure out
 * basic types -- epub, jar, ear, war, kmz and StarOffice
 */
public class DeprecatedZipContainerDetector {




/*
TODO: move this to the apple module
    private static MediaType detectIWork13(ZipFile zip) {
        if (zip.getEntry(IWork13PackageParser.IWORK13_COMMON_ENTRY) != null) {
            return IWork13PackageParser.IWork13DocumentType.detect(zip);
        }
        return null;
    }

    private static MediaType detectIWork18(ZipFile zip) {
        return IWork18PackageParser.IWork18DocumentType.detect(zip);
    }

    private static MediaType detectIWork(ZipFile zip) {
        if (zip.getEntry(IWorkPackageParser.IWORK_COMMON_ENTRY) != null) {
            // Locate the appropriate index file entry, and reads from that
            // the root element of the document. That is used to the identify
            // the correct type of the keynote container.
            for (String entryName : IWorkPackageParser.IWORK_CONTENT_ENTRIES) {
               IWORKDocumentType type = IWORKDocumentType.detectType(zip.getEntry(entryName), zip); 
               if (type != null) {
                  return type.getType();
               }
            }
            
            // Not sure, fallback to the container type
            return MediaType.application("vnd.apple.iwork");
        } else {
            return null;
        }
    }
    */





}
