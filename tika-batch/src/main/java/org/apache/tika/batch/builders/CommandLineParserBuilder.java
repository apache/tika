package org.apache.tika.batch.builders;

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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads configurable options from a config file and returns org.apache.commons.cli.Options
 * object to be used in commandline parser.  This allows users and developers to set
 * which options should be made available via the commandline.
 */
public class CommandLineParserBuilder {

    public Options build(InputStream is) throws IOException {
        Document doc = null;
        DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = null;
        try {
            docBuilder = fact.newDocumentBuilder();
            doc = docBuilder.parse(is);
        } catch (ParserConfigurationException e) {
            throw new IOException(e);
        } catch (SAXException e) {
            throw new IOException(e);
        }
        Node docElement = doc.getDocumentElement();
        NodeList children = docElement.getChildNodes();
        Node commandlineNode = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String nodeName = child.getNodeName();
            if (nodeName.equals("commandline")) {
                commandlineNode = child;
                break;
            }
        }
        Options options = new Options();
        if (commandlineNode == null) {
            return options;
        }
        NodeList optionNodes = commandlineNode.getChildNodes();
        for (int i = 0; i < optionNodes.getLength(); i++) {

            Node optionNode = optionNodes.item(i);
            if (optionNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Option opt = buildOption(optionNode);
            if (opt != null) {
                options.addOption(opt);
            }
        }
        return options;
    }

    private Option buildOption(Node optionNode) {
        NamedNodeMap map = optionNode.getAttributes();
        String opt = getString(map, "opt", "");
        String description = getString(map, "description", "");
        String longOpt = getString(map, "longOpt", "");
        boolean isRequired = getBoolean(map, "required", false);
        boolean hasArg = getBoolean(map, "hasArg", false);
        if(opt.trim().length() == 0 || description.trim().length() == 0) {
            throw new IllegalArgumentException(
                    "Must specify at least option and description");
        }
        Option option = new Option(opt, description);
        if (longOpt.trim().length() > 0) {
            option.setLongOpt(longOpt);
        }
        if (isRequired) {
            option.setRequired(true);
        }
        if (hasArg) {
            option.setArgs(1);
        }
        return option;
    }

    private boolean getBoolean(NamedNodeMap map, String opt, boolean defaultValue) {
        Node n = map.getNamedItem(opt);
        if (n == null) {
            return defaultValue;
        }

        if (n.getNodeValue() == null) {
            return defaultValue;
        }

        if (n.getNodeValue().toLowerCase(Locale.ROOT).equals("true")) {
            return true;
        } else if (n.getNodeValue().toLowerCase(Locale.ROOT).equals("false")) {
            return false;
        }
        return defaultValue;
    }

    private String getString(NamedNodeMap map, String opt, String defaultVal) {
        Node n = map.getNamedItem(opt);
        if (n == null) {
            return defaultVal;
        }
        String value = n.getNodeValue();

        if (value == null) {
            return defaultVal;
        }
        return value;
    }


}
