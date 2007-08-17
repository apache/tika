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
package org.apache.tika.parser.mspowerpoint;

// JDK imports
import java.io.InputStream;

import org.apache.tika.utils.MSExtractor;

import org.apache.log4j.Logger;
import org.apache.poi.poifs.eventfilesystem.POIFSReader;

/**
 * Converts the Powerpoint document content to plain text.
 * 
 * @author Stephan Strittmatter - http://www.sybit.de
 * @author J&eacute;r&ocirc;me Charron
 */
class PPTExtractor extends MSExtractor {

    static Logger LOG = Logger.getRootLogger();

    private StringBuffer text = null;

    private POIFSReader reader = null;

    public String extractText(InputStream input) throws Exception {
        this.reader = new POIFSReader();
        this.text = new StringBuffer();
        reader.registerListener(new ContentReaderListener(this.text),
                PPTConstants.POWERPOINT_DOCUMENT);
        // input.reset();
        if (input.available() > 0) {
            this.reader.read(input);

            LOG.warn("Input <=0 :" + input.available());

        }
        return (this.text != null) ? text.toString() : null;

    }
}
