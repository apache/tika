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
package org.apache.tika.parser.microsoft.ooxml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.utils.StringUtils;
import org.apache.tika.utils.XMLReaderUtils;

public class CommentPersonHandler extends DefaultHandler {

    private final Metadata metadata;

    CommentPersonHandler(Metadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        //what else do we want?
        //<person displayName="Wiley Coyote" id="{11111111-2234-2342-2342-23498237923}" userId="55bbdf23486284" providerId="Windows Live"/>
        if ("person".equals(localName)) {
            String displayName = XMLReaderUtils.getAttrValue("displayName", atts);
            if (!StringUtils.isBlank(displayName)) {
                metadata.add(Office.COMMENT_PERSONS, displayName);
            }
        }
    }
}
