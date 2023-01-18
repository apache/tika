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
package org.apache.tika.parser.pdf.xmpschemas;

import java.io.IOException;
import java.util.Calendar;

import org.apache.jempbox.xmp.XMPMetadata;
import org.apache.jempbox.xmp.XMPSchema;
import org.w3c.dom.Element;

public class XMPSchemaPDFVT extends XMPSchema {
    public static final String NAMESPACE_URI = "http://www.npes.org/pdfvt/ns/id/";
    public static final String NAMESPACE = "pdfvtid";

    private Calendar vTModified;
    public XMPSchemaPDFVT(XMPMetadata parent) {
        super(parent, NAMESPACE, NAMESPACE_URI);
    }

    public XMPSchemaPDFVT(Element element, String prefix) {
        super(element, prefix);
    }

    public String getPDFVTVersion() {
        return this.getTextProperty(this.prefix + ":GTS_PDFVTVersion");
    }

    public Calendar getPDFVTModified() throws IOException {
        return this.getDateProperty(this.prefix + ":GTS_PDFVTModDate");
    }
}
