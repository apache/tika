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
package org.apache.tika.fork;

import org.apache.tika.metadata.Metadata;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class MetadataContentHandler extends DefaultHandler {

    private final Metadata metadata;

    public MetadataContentHandler(Metadata metadata) {
        this.metadata = metadata;
    }

    public void startElement(
            String uri, String local, String name, Attributes attributes)
            throws SAXException {
        if ("meta".equals(local)) {
            String aname = attributes.getValue("name");
            String content = attributes.getValue("content");
            metadata.add(aname, content);
        }
    }

}
