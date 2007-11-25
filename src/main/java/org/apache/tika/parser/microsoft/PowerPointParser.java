/**
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

import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/**
 * Power point parser
 */
public class PowerPointParser extends OfficeParser {

    /**
     *  Name of a PowerPoint document within a POIFS file system
     */
    private  static final String POWERPOINT = "PowerPoint Document";

    protected String getContentType() {
        return "application/vnd.ms-powerpoint";
    }

    protected void extractText(POIFSFileSystem filesystem, Appendable builder) throws IOException {

        InputStream stream = filesystem.createDocumentInputStream(POWERPOINT);
        try {
            new PowerPointExtractor(builder).extract(stream);
        } finally {
            stream.close();
        }
    }

}
