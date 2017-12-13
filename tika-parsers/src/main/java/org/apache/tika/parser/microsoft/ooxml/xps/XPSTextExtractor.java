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

package org.apache.tika.parser.microsoft.ooxml.xps;

import org.apache.poi.POIXMLDocument;
import org.apache.poi.POIXMLProperties;
import org.apache.poi.POIXMLTextExtractor;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.xmlbeans.XmlException;

import java.io.IOException;

/**
 * Currently, mostly a pass-through class to hold pkg and properties
 * and keep the general framework similar to our other POI-integrated
 * extractors.
 */
public class XPSTextExtractor extends POIXMLTextExtractor {

    private final OPCPackage pkg;
    private final POIXMLProperties properties;

    public XPSTextExtractor(OPCPackage pkg) throws OpenXML4JException, XmlException, IOException {
        super((POIXMLDocument)null);
        this.pkg = pkg;
        this.properties = new POIXMLProperties(pkg);

    }

    @Override
    public OPCPackage getPackage() {
        return pkg;
    }

    @Override
    public String getText() {
        return null;
    }
    public POIXMLProperties.CoreProperties getCoreProperties() {
        return this.properties.getCoreProperties();
    }

    public POIXMLProperties.ExtendedProperties getExtendedProperties() {
        return this.properties.getExtendedProperties();
    }

    public POIXMLProperties.CustomProperties getCustomProperties() {
        return this.properties.getCustomProperties();
    }
}
