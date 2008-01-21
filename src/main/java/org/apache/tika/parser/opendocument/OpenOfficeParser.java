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

package org.apache.tika.parser.opendocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * OpenOffice parser
 */
public class OpenOfficeParser implements Parser {

    private Parser meta = new OpenOfficeMetaParser();

    private Parser content = new OpenOfficeContentParser();

    public Parser getMetaParser() {
        return meta;
    }

    public void setMetaParser(Parser meta) {
        this.meta = meta;
    }

    public Parser getContentParser() {
        return content;
    }

    public void setContentParser(Parser content) {
        this.content = content;
    }

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        ZipInputStream zip = new ZipInputStream(stream);
        ZipEntry entry = zip.getNextEntry();
        while (entry != null) {
            if (entry.getName().equals("mimetype")) {
                StringBuilder buffer = new StringBuilder();
                Reader reader = new InputStreamReader(zip, "UTF-8");
                for (int ch = reader.read(); ch != -1; ch = reader.read()) {
                    buffer.append((char) ch);
                }
                metadata.set(Metadata.CONTENT_TYPE, buffer.toString());
            } else if (entry.getName().equals("meta.xml")) {
                meta.parse(zip, new DefaultHandler(), metadata);
            } else if (entry.getName().equals("content.xml")) {
                content.parse(zip, handler, metadata);
            }
            entry = zip.getNextEntry();
        }
    }

}
