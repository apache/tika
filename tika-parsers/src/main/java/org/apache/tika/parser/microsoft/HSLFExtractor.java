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
package org.apache.tika.parser.microsoft;

import org.apache.poi.hslf.extractor.PowerPointExtractor;
import org.apache.poi.hslf.model.OLEShape;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

public class HSLFExtractor extends AbstractPOIFSExtractor {
    public HSLFExtractor(ParseContext context) {
        super(context);
    }

    protected void parse(
            POIFSFileSystem filesystem, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        PowerPointExtractor powerPointExtractor =
            new PowerPointExtractor(filesystem);
        xhtml.element("p", powerPointExtractor.getText(true, true));

        List<OLEShape> shapeList = powerPointExtractor.getOLEShapes();
        for (OLEShape shape : shapeList) {
            TikaInputStream stream =
                TikaInputStream.get(shape.getObjectData().getData());
            try {
                String mediaType = null;
                if ("Excel.Chart.8".equals(shape.getProgID())) {
                    mediaType = "application/vnd.ms-excel";
                }
                handleEmbeddedResource(
                        stream, Integer.toString(shape.getObjectID()),
                        mediaType, xhtml, false);
            } finally {
                stream.close();
            }
        }
    }
}
