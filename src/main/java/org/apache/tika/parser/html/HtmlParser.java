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

import org.apache.log4j.Logger;
import org.apache.tika.config.Content;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.Parser;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.tidy.Tidy;

/**
 * Html parser
 * 
 */
public class HtmlParser extends Parser {

    static Logger logger = Logger.getRootLogger();

    protected String parse(InputStream stream, Iterable<Content> contents)
            throws IOException, TikaException {
        Tidy tidy = new Tidy();
        tidy.setQuiet(true);
        tidy.setShowWarnings(false);
        Node root = tidy.parseDOM(stream, null).getDocumentElement();
        for (Content content : contents) {
            String text = content.getTextSelect();
            if (text != null && !text.equalsIgnoreCase("fulltext")
                    && !text.equalsIgnoreCase("summary")) {
                extractElementTxt((Element) root, content);
            }
        }
        return getTextContent(root);
    }

    private void extractElementTxt(Element root, Content content) {
        NodeList children = root.getElementsByTagName(content.getTextSelect());
        if (children != null) {
            if (children.getLength() > 0) {
                if (children.getLength() == 1) {
                    Element node = (Element) children.item(0);
                    Text txt = (Text) node.getFirstChild();
                    if (txt != null) {
                        content.setValue(txt.getData());

                    }
                } else {
                    String[] values = new String[100];
                    for (int i = 0; i < children.getLength(); i++) {
                        Element node = (Element) children.item(i);
                        Text txt = (Text) node.getFirstChild();
                        if (txt != null) {
                            values[i] = txt.getData();
                        }
                    }
                    if (values.length > 0) {
                        content.setValue(values[0]);
                        content.setValues(values);
                    }
                }
            }
        }
    }

    private String getTextContent(Node node) {
        NodeList children = node.getChildNodes();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            switch (child.getNodeType()) {
            case Node.ELEMENT_NODE:
                sb.append(getTextContent(child));
                sb.append(" ");
                break;
            case Node.TEXT_NODE:
                sb.append(((Text) child).getData());
                break;
            }
        }
        return sb.toString();
    }

}
