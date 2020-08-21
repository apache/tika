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
import java.util.Map;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.Property;

class ExtendedPropertiesHandler extends CorePropertiesHandler {

    final static String EP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties";

    public ExtendedPropertiesHandler(Metadata metadata) {
        super(metadata);
    }

    @Override
    void addProperties() {
        Map<String, Property> ep = properties.get(EP_NS);
        if (ep == null) {
            ep = new HashMap<>();
        }
        ep.put("AppVersion", OfficeOpenXMLExtended.APP_VERSION);
        ep.put("Application", OfficeOpenXMLExtended.APPLICATION);
        ep.put("Comments", OfficeOpenXMLExtended.COMMENTS);
        ep.put("Company", OfficeOpenXMLExtended.COMPANY);
        ep.put("DocSecurity", OfficeOpenXMLExtended.DOC_SECURITY);
        ep.put("HiddenSlides", OfficeOpenXMLExtended.HIDDEN_SLIDES);
        ep.put("Manager", OfficeOpenXMLExtended.MANAGER);
        ep.put("Notes", OfficeOpenXMLExtended.NOTES);
        ep.put("PresentationFormat", OfficeOpenXMLExtended.PRESENTATION_FORMAT);
        ep.put("Template", OfficeOpenXMLExtended.TEMPLATE);
        ep.put("TotalTime", OfficeOpenXMLExtended.TOTAL_TIME);
        ep.put("Pages", Office.PAGE_COUNT);
        ep.put("Words", Office.WORD_COUNT);
        ep.put("Characters", Office.CHARACTER_COUNT);
        ep.put("CharactersWithSpaces", Office.CHARACTER_COUNT_WITH_SPACES);
        ep.put("Paragraphs", Office.PARAGRAPH_COUNT);
        ep.put("Lines", Office.LINE_COUNT);
        properties.put(EP_NS, ep);
    }

    @Override
    public String getContentType() {
        return "application/vnd.openxmlformats-officedocument.extended-properties+xml";
    }
}
