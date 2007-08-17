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
package org.apache.tika.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

/**
 * Parse xml config file. Use cache mecanisme to store multiple configs   
 * @author Rida Benjelloun (ridabenjelloun@apache.org)  
 */
public class LiusConfig {

    static Logger logger = Logger.getRootLogger();

    private static Map configsCache = new HashMap<String, LiusConfig>();

    private static List<ParserConfig> parsersConfigs;

    private static LiusConfig tc;

    private LiusConfig() {
    }

    private static String currentFile;

    public static LiusConfig getInstance(String configFile) {

        if (configsCache.containsKey(configFile)) {
            return (LiusConfig) configsCache.get(configFile);

        } else {
            Document doc = parse(configFile);

            tc = new LiusConfig();

            populateConfig(doc, tc);

            configsCache.put(configFile, tc);
        }
        currentFile = configFile;
        return tc;
    }

    public List<ParserConfig> getParsersConfigs() {
        return parsersConfigs;
    }

    public void setParsersConfigs(List<ParserConfig> parsersConfigs) {
        this.parsersConfigs = parsersConfigs;
    }

    public ParserConfig getParserConfig(String mimeType) {
        ParserConfig pc = null;
        for (int i = 0; i < parsersConfigs.size(); i++) {
            if (((ParserConfig) parsersConfigs.get(i)).getMimes().containsKey(
                    mimeType)) {
                return (ParserConfig) parsersConfigs.get(i);
            }
        }
        return pc;
    }

    private static Document parse(String file) {
        org.jdom.Document xmlDoc = new org.jdom.Document();
        try {
            SAXBuilder builder = new SAXBuilder();
            xmlDoc = builder.build(new File(file));
        } catch (JDOMException e) {
            logger.error(e.getMessage());
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return xmlDoc;

    }

    private static void populateConfig(Document doc, LiusConfig tc) {
        parsersConfigs = new ArrayList<ParserConfig>();
        try {
            List parsersList = XPath.selectNodes(doc, "//parser");
            for (int i = 0; i < parsersList.size(); i++) {
                ParserConfig pc = new ParserConfig();
                Element parserElem = (Element) parsersList.get(i);
                pc.setName(parserElem.getAttributeValue("name"));
                pc.setParserClass(parserElem.getAttributeValue("class"));
                if (parserElem.getChild("namespace") != null) {
                    pc.setNameSpace(parserElem.getChild("namespace")
                            .getTextTrim());
                }
                Map<String, String> mimes = new HashMap<String, String>();
                List mimesElems = parserElem.getChildren("mime");
                for (int j = 0; j < mimesElems.size(); j++) {
                    String mime = ((Element) mimesElems.get(j)).getTextTrim();
                    mimes.put(mime, null);
                }
                pc.setMimes(mimes);
                List<Content> contents = new ArrayList<Content>();
                if (parserElem.getChild("extract") != null) {
                    List contentsElems = parserElem.getChild("extract")
                            .getChildren();
                    for (int j = 0; j < contentsElems.size(); j++) {
                        Content content = new Content();
                        Element contentElem = (Element) contentsElems.get(j);
                        content.setName(contentElem.getAttributeValue("name"));
                        if (contentElem.getAttribute("xpathSelect") != null) {
                            content.setXPathSelect(contentElem
                                    .getAttributeValue("xpathSelect"));
                        }
                        if (contentElem.getAttribute("textSelect") != null) {
                            content.setTextSelect(contentElem
                                    .getAttributeValue("textSelect"));
                        }
                        if (contentElem.getChild("regexSelect") != null) {
                            content.setRegexSelect(contentElem.getChild(
                                    "regexSelect").getTextTrim());
                        }
                        contents.add(content);
                    }
                }
                pc.setContents(contents);
                parsersConfigs.add(pc);
            }
        } catch (JDOMException e) {
            logger.error(e.getMessage());
        }
        tc.setParsersConfigs(parsersConfigs);

    }

}
