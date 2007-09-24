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
import java.util.HashMap;
import java.util.Map;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

/**
 * Parse xml config file.
 */
public class LiusConfig {

    private final Map<String, ParserConfig> configs =
        new HashMap<String, ParserConfig>();

    public LiusConfig(String file) throws JDOMException, IOException {
        Document document = new SAXBuilder().build(new File(file));
        for (Object element : XPath.selectNodes(document, "//parser")) {
            ParserConfig pc = new ParserConfig((Element) element);
            for (Object child : ((Element) element).getChildren("mime")) {
                configs.put(((Element) child).getTextTrim(), pc);
            }
        }
    }

    public ParserConfig getParserConfig(String mimeType) {
        return configs.get(mimeType);
    }

}
