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

import org.apache.jempbox.xmp.XMPMetadata;
import org.apache.jempbox.xmp.XMPSchema;
import org.w3c.dom.Element;

/**
 * This is somewhat of a hack to handle the older pdfx:
 * See also the more modern {@link XMPSchemaPDFXId}
 */
public class XMPSchemaPDFX extends XMPSchema {
    public static final String NAMESPACE_URI = "http://ns.adobe.com/pdfx/1.3/";
    public static final String NAMESPACE = "pdfx";

    public XMPSchemaPDFX(XMPMetadata parent) {
        super(parent, NAMESPACE, NAMESPACE_URI);
    }

    public XMPSchemaPDFX(Element element, String prefix) {
        super(element, prefix);
    }

    public String getPDFXVersion() {
        return this.getTextProperty(this.prefix + ":GTS_PDFXVersion");
    }

    public String getPDFXConformance() {
        return this.getTextProperty(this.prefix + ":GTS_PDFXConformance");
    }
}
