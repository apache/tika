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

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.POIXMLTextExtractor;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

public class POIXMLTextExtractorDecorator extends AbstractOOXMLExtractor {

    public POIXMLTextExtractorDecorator(ParseContext context, POIXMLTextExtractor extractor) {
        super(context, extractor);
    }

    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml) throws SAXException {
        // extract document content as a single string (not structured)
        xhtml.element("p", extractor.getText());
    }

    @Override
    protected List<PackagePart> getMainDocumentParts() {
       return new ArrayList<PackagePart>();
    }
}
