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

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.Content;
import org.apache.tika.parser.Parser;
import org.apache.tika.utils.RegexUtils;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.tidy.Tidy;

/**
 * Html parser
 * @author Rida Benjelloun (ridabenjelloun@apache.org)
 */
public class HtmlParser extends Parser {

    static Logger logger = Logger.getRootLogger();

    private Node root = null;

    private String contentStr;

    private Map<String, Content> contentsMap;

    public Content getContent(String name) {
        if (contentsMap == null || contentsMap.isEmpty()) {
            getContents();
        }
        return contentsMap.get(name);
    }

    public List<Content> getContents() {
        if (contentStr == null) {
            contentStr = getStrContent();
        }
        List<Content> ctt = getParserConfig().getContents();
        contentsMap = new HashMap<String, Content>();
        Iterator i = ctt.iterator();
        while (i.hasNext()) {
            Content ct = (Content) i.next();
            if (ct.getTextSelect() != null) {
                if (ct.getTextSelect().equalsIgnoreCase("fulltext")) {
                    ct.setValue(contentStr);
                } else {
                    extractElementTxt((Element) root, ct);
                }

            }

            else if (ct.getRegexSelect() != null) {
                try {
                    List<String> valuesLs = RegexUtils.extract(contentStr, ct
                            .getRegexSelect());
                    if (valuesLs.size() > 0) {
                        ct.setValue(valuesLs.get(0));
                        ct.setValues(valuesLs.toArray(new String[0]));
                    }
                } catch (MalformedPatternException e) {
                    logger.error(e.getMessage());
                }
            }
            contentsMap.put(ct.getName(), ct);
        }

        return getParserConfig().getContents();

    }

    public String getStrContent() {
        if (root == null)
            root = getRoot(getInputStream());
        contentStr = getTextContent(root);
        return contentStr;
    }

    private Node getRoot(InputStream is) {
        Tidy tidy = new Tidy();
        tidy.setQuiet(true);
        tidy.setShowWarnings(false);
        org.w3c.dom.Document doc = tidy.parseDOM(is, null);
        return doc.getDocumentElement();
    }

    private void extractElementTxt(Element root, Content content) {

        NodeList children = root.getElementsByTagName(content.getName());
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
