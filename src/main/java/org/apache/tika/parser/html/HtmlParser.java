/**
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
package org.apache.tika.parser.html;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.tidy.Tidy;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Html parser
 */
public class HtmlParser implements Parser {

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        try {
            Tidy tidy = new Tidy();
            tidy.setQuiet(true);
            tidy.setShowWarnings(false);
            tidy.setXHTML(true);

            Element root = tidy.parseDOM(stream, null).getDocumentElement();

            metadata.set(Metadata.CONTENT_TYPE, "text/html");
            extractElementTxt(root, Metadata.TITLE, "title", metadata);

            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.transform(new DOMSource(root), new SAXResult(handler));
        } catch (TransformerException e) {
            throw new TikaException("Failed to transform DOM to SAX", e);
        }
    }

    private void extractElementTxt(
            Element root, String name, String tag, Metadata metadata) {
        NodeList children = root.getElementsByTagName(tag);
        if (children != null) {
            if (children.getLength() > 0) {
                if (children.getLength() == 1) {
                    Element node = (Element) children.item(0);
                    Text txt = (Text) node.getFirstChild();
                    if (txt != null) {
                        metadata.set(name, txt.getData());
                    }
                } else {
                    for (int i = 0; i < children.getLength(); i++) {
                        Element node = (Element) children.item(i);
                        Text txt = (Text) node.getFirstChild();
                        if (txt != null) {
                            metadata.add(name, txt.getData());
                        }
                    }
                }
            }
        }
    }

}
