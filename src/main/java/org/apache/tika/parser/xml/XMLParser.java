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
package org.apache.tika.parser.xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.Content;
import org.apache.tika.parser.Parser;
import org.apache.tika.utils.RegexUtils;
import org.apache.tika.utils.Utils;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
import org.jaxen.JaxenException;
import org.jaxen.SimpleNamespaceContext;
import org.jaxen.jdom.JDOMXPath;
import org.jdom.Attribute;
import org.jdom.CDATA;
import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.EntityRef;
import org.jdom.Namespace;
import org.jdom.ProcessingInstruction;
import org.jdom.Text;

/**
 * XML parser
 * 
 * @author Rida Benjelloun (ridabenjelloun@apache.org)
 */
public class XMLParser extends Parser {
    static Logger logger = Logger.getRootLogger();

    private Document xmlDoc = null;

    private SimpleNamespaceContext nsc = new SimpleNamespaceContext();

    private Map<String, Content> contentsMap;

    private String contentStr;

    public Content getContent(String name) {
        if (contentsMap == null || contentsMap.isEmpty()) {
            getContents();
        }
        return contentsMap.get(name);
    }

    public String getStrContent() {
        if (xmlDoc == null)
            xmlDoc = Utils.parse(getInputStream());
        contentStr = concatOccurance(xmlDoc, "//*", " ");
        return contentStr;
    }

    public List<Content> getContents() {
        if (contentStr == null) {
            contentStr = getStrContent();
        }
        if (xmlDoc == null)
            xmlDoc = Utils.parse(getInputStream());
        List<String> documentNs = getAllDocumentNs(xmlDoc);
        List<Content> ctt = getParserConfig().getContents();
        Iterator it = ctt.iterator();
        contentsMap = new HashMap<String, Content>();
        if (exist(documentNs, getParserConfig().getNameSpace())) {
            while (it.hasNext()) {
                Content content = (Content) it.next();
                if (content.getXPathSelect() != null) {
                    extractContent(xmlDoc, content, contentsMap);
                } else if (content.getRegexSelect() != null) {
                    try {
                        List<String> valuesLs = RegexUtils.extract(contentStr,
                                content.getRegexSelect());
                        if (valuesLs.size() > 0) {
                            content.setValue(valuesLs.get(0));
                            content.setValues(valuesLs.toArray(new String[0]));
                        }
                    } catch (MalformedPatternException e) {
                        logger.error(e.getMessage());
                    }
                }

            }
        }
        return getParserConfig().getContents();
    }

    public String concatOccurance(Object xmlDoc, String xpath, String concatSep) {

        StringBuffer chaineConcat = new StringBuffer();
        try {
            JDOMXPath xp = new JDOMXPath(xpath);
            xp.setNamespaceContext(nsc);
            List ls = xp.selectNodes(xmlDoc);
            Iterator i = ls.iterator();
            int j = 0;
            while (i.hasNext()) {
                j++;
                String text = "";
                Object obj = (Object) i.next();
                if (obj instanceof Element) {
                    Element elem = (Element) obj;
                    text = elem.getText().trim();
                } else if (obj instanceof Attribute) {
                    Attribute att = (Attribute) obj;
                    text = att.getValue().trim();
                } else if (obj instanceof Text) {
                    Text txt = (Text) obj;
                    text = txt.getText().trim();
                } else if (obj instanceof CDATA) {
                    CDATA cdata = (CDATA) obj;
                    text = cdata.getText().trim();
                } else if (obj instanceof Comment) {
                    Comment com = (Comment) obj;
                    text = com.getText().trim();
                } else if (obj instanceof ProcessingInstruction) {
                    ProcessingInstruction pi = (ProcessingInstruction) obj;
                    text = pi.getData().trim();
                } else if (obj instanceof EntityRef) {
                    EntityRef er = (EntityRef) obj;
                    text = er.toString().trim();
                }
                if (text != "") {
                    if (ls.size() == 1) {
                        chaineConcat.append(text);
                        return chaineConcat.toString().trim();
                    } else {
                        if (ls.size() == j)
                            chaineConcat.append(text);
                        else
                            chaineConcat.append(text + " " + concatSep + " ");
                    }
                }
            }
        } catch (JaxenException j) {
            logger.error(j.getMessage());
        }
        return chaineConcat.toString().trim();
    }

    public List getAllDocumentNs(org.jdom.Document doc) {
        List ls = new ArrayList();
        processChildren(doc.getRootElement(), ls);
        return ls;
    }

    private boolean exist(List nsLs, String nsUri) {
        if (nsLs.isEmpty())
            return false;
        for (int i = 0; i < nsLs.size(); i++) {
            if (((String) nsLs.get(i)).equals(nsUri)) {
                return true;
            }
        }
        return false;
    }

    private void processChildren(Element elem, List ns) {
        Namespace nsCourent = (Namespace) elem.getNamespace();
        String nsUri = (nsCourent.getURI());
        if (!exist(ns, nsUri)) {
            ns.add(nsUri.trim());
            nsc.addNamespace(nsCourent.getPrefix(), nsCourent.getURI());
        }
        List additionalNs = elem.getAdditionalNamespaces();
        if (!additionalNs.isEmpty())
            copyNsList(additionalNs, ns);
        if (elem.getChildren().size() > 0) {
            List elemChildren = elem.getChildren();
            for (int i = 0; i < elemChildren.size(); i++) {
                processChildren((Element) elemChildren.get(i), ns);
            }
        }
    }

    private void copyNsList(List nsElem, List nsRes) {
        for (int i = 0; i < nsElem.size(); i++) {
            Namespace ns = (Namespace) nsElem.get(i);
            nsc.addNamespace(ns.getPrefix(), ns.getURI());
            nsRes.add(ns.getURI().trim());
        }
    }

    public void extractContent(Document xmlDoc, Content content,
            Map<String, Content> contentsMap) {
        try {
            JDOMXPath xp = new JDOMXPath(content.getXPathSelect());
            xp.setNamespaceContext(nsc);
            List selectNodes = xp.selectNodes(xmlDoc);
            Iterator nodes = selectNodes.iterator();
            String[] values = new String[selectNodes.size()];
            int i = 0;
            while (nodes.hasNext()) {
                Object node = nodes.next();
                if (node instanceof Element) {
                    Element elem = (Element) node;
                    if (elem.getText().trim() != null
                            && elem.getText().trim() != "") {
                        values[i] = elem.getText().trim();
                    }
                } else if (node instanceof Attribute) {
                    Attribute att = (Attribute) node;
                    values[i] = att.getValue();
                } else if (node instanceof Text) {
                    Text text = (Text) node;
                    values[i] = text.getText();
                } else if (node instanceof CDATA) {
                    CDATA cdata = (CDATA) node;
                    values[i] = cdata.getText();
                } else if (node instanceof Comment) {
                    Comment com = (Comment) node;
                    values[i] = com.getText();
                } else if (node instanceof ProcessingInstruction) {
                    ProcessingInstruction pi = (ProcessingInstruction) node;
                    values[i] = pi.getData();
                } else if (node instanceof EntityRef) {
                    EntityRef er = (EntityRef) node;
                    values[i] = er.toString();

                }
                i++;
            }
            if (values.length > 0) {
                content.setValue(values[0]);
                content.setValues(values);
                contentsMap.put(content.getName(), content);
            }
        } catch (JaxenException e) {
            logger.error(e.getMessage());
        }

    }

    public void setDocNs(List ns) {
        for (int i = 0; i < ns.size(); i++) {

            // nsc.addNamespace((String)ns.get(i);
        }

    }

}
