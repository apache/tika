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

package org.apache.tika.parser.microsoft.ooxml.xwpf.ml2006;


import org.apache.poi.openxml4j.opc.ContentTypes;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

class RelationshipsHandler extends AbstractPartHandler {

    final static String REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";

    private final RelationshipsManager relationshipsManager;

    public RelationshipsHandler(RelationshipsManager relationshipsManager) {
        this.relationshipsManager = relationshipsManager;
    }


    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void endDocument() throws SAXException {
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if (uri.equals(REL_NS)) {
            if (localName.equals("Relationship")) {
                String id = atts.getValue("", "Id");
                String type = atts.getValue("", "Type");
                String target = atts.getValue("", "Target");
                String targetModeString = atts.getValue("", "TargetMode");
                TargetMode targetMode = "EXTERNAL".equals(targetModeString)? TargetMode.EXTERNAL :
                        TargetMode.INTERNAL;
                relationshipsManager.addRelationship(getName(), id, type, target, targetMode);
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {

    }

    @Override
    public String getContentType() {
        return ContentTypes.RELATIONSHIPS_PART;
    }

}
