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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.tika.config.Content;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.utils.Utils;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jaxen.JaxenException;
import org.jaxen.SimpleNamespaceContext;
import org.jaxen.jdom.JDOMXPath;
import org.jdom.Attribute;
import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.EntityRef;
import org.jdom.Namespace;
import org.jdom.ProcessingInstruction;
import org.jdom.Text;

/**
 * XML parser
 */
public class XMLParser implements Parser {

    static Logger logger = Logger.getRootLogger();

    private SimpleNamespaceContext nsc = new SimpleNamespaceContext();

    private String namespace;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String parse(
            InputStream stream, Iterable<Content> contents, Metadata metadata)
            throws IOException, TikaException {
        Document xmlDoc = Utils.parse(stream);
        if (exist(getAllDocumentNs(xmlDoc), getNamespace())) {
            for (Content content : contents) {
                if (content.getXPathSelect() != null) {
                    extractContent(xmlDoc, content, metadata);
                }
            }
        }
        return concatOccurrence(xmlDoc, "//*", " ");
    }

    public String concatOccurrence(Object xmlDoc, String xpath, String concatSep) {

        StringBuilder chaineConcat = new StringBuilder();
        try {
            JDOMXPath xp = new JDOMXPath(xpath);
            xp.setNamespaceContext(nsc);
            List ls = xp.selectNodes(xmlDoc);
            Iterator i = ls.iterator();
            int j = 0;
            while (i.hasNext()) {
                j++;
                String text = "";
                Object obj = i.next();
                if (obj instanceof Element) {
                    Element elem = (Element) obj;
                    text = elem.getText().trim();
                } else if (obj instanceof Attribute) {
                    Attribute att = (Attribute) obj;
                    text = att.getValue().trim();
                } else if (obj instanceof Text) {
                    Text txt = (Text) obj;
                    text = txt.getText().trim();
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
                if (StringUtils.isNotEmpty(text)) {
                    chaineConcat.append(text);
                    if (ls.size() == 1) {
                        return chaineConcat.toString().trim();
                    } else {
                        if (ls.size() != j) {
                            chaineConcat.append(' ')
                                    .append(concatSep)
                                    .append(' ');
                        }
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
        for (Object nsL : nsLs) {
            if (nsL.equals(nsUri)) {
                return true;
            }
        }
        return false;
    }

    private void processChildren(Element elem, List ns) {
        Namespace nsCourent = elem.getNamespace();
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
            for (Object anElemChildren : elemChildren) {
                processChildren((Element) anElemChildren, ns);
            }
        }
    }

    private void copyNsList(List nsElem, List nsRes) {
        for (Object aNsElem : nsElem) {
            Namespace ns = (Namespace) aNsElem;
            nsc.addNamespace(ns.getPrefix(), ns.getURI());
            nsRes.add(ns.getURI().trim());
        }
    }

    public void extractContent(
            Document xmlDoc, Content content, Metadata metadata) {
        try {
            JDOMXPath xp = new JDOMXPath(content.getXPathSelect());
            xp.setNamespaceContext(nsc);
            List selectNodes = xp.selectNodes(xmlDoc);
            Iterator nodes = selectNodes.iterator();
            while (nodes.hasNext()) {
                Object node = nodes.next();
                if (node instanceof Element) {
                    Element elem = (Element) node;
                    if (StringUtils.isNotBlank(elem.getText())) {
                        metadata.add(content.getName(), elem.getText().trim());
                    }
                } else if (node instanceof Attribute) {
                    Attribute att = (Attribute) node;
                    metadata.add(content.getName(), att.getValue());
                } else if (node instanceof Text) {
                    Text text = (Text) node;
                    metadata.add(content.getName(), text.getText());
                } else if (node instanceof Comment) {
                    Comment com = (Comment) node;
                    metadata.add(content.getName(), com.getText());
                } else if (node instanceof ProcessingInstruction) {
                    ProcessingInstruction pi = (ProcessingInstruction) node;
                    metadata.add(content.getName(), pi.getData());
                } else if (node instanceof EntityRef) {
                    EntityRef er = (EntityRef) node;
                    metadata.add(content.getName(), er.toString());
                }
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
