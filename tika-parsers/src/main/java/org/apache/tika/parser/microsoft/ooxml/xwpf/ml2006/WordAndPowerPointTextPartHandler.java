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

import java.util.HashMap;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.ooxml.OOXMLWordAndPowerPointTextHandler;
import org.apache.tika.parser.microsoft.ooxml.OOXMLTikaBodyPartHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;


/**
 * Simple wrapper/extension of OOXMLWordAndPowerPointTextHandler to fit
 * into the inline parsing scheme.
 */
class WordAndPowerPointTextPartHandler extends OOXMLWordAndPowerPointTextHandler implements PartHandler {

    private final String contentType;
    private String name;
    public WordAndPowerPointTextPartHandler(String contentType, XHTMLContentHandler xhtml,
                                            RelationshipsManager relationshipsManager,
                                            OfficeParserConfig officeParserConfig) {
        super(new OOXMLTikaBodyPartHandler(xhtml, null, null, officeParserConfig),
                new HashMap<String, String>());
        this.contentType = contentType;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public void endPart() throws SAXException, TikaException {
        //no-op
    }
}
