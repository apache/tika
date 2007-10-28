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

//JDK imports


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypesFactory;
import org.apache.tika.parser.Parser;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

/**
 * Parse xml config file.
 */
public class TikaConfig {

    public static final String DEFAULT_CONFIG_LOCATION = 
        "/org/apache/tika/tika-config.xml";

    private final Map<String, Parser> parsers = new HashMap<String, Parser>();
    
    private static MimeTypes mimeTypes;

    public TikaConfig(String file) throws JDOMException, IOException {
        this(new File(file));
    }

    public TikaConfig(File file) throws JDOMException, IOException {
        this(new SAXBuilder().build(file));
    }

    public TikaConfig(URL url) throws JDOMException, IOException {
        this(new SAXBuilder().build(url));
    }

    public TikaConfig(InputStream stream) throws JDOMException, IOException {
        this(new SAXBuilder().build(stream));
    }

    public TikaConfig(Document document) throws JDOMException, IOException {
        this(document.getRootElement());
    }

    public TikaConfig(Element element) throws JDOMException, IOException {
        Element mtr = element.getChild("mimeTypeRepository");
        String mimeTypeRepoResource = mtr.getAttributeValue("resource");
        mimeTypes = MimeTypesFactory.create(mimeTypeRepoResource);

        for (Object node : XPath.selectNodes(element, "//parser")) {
            String className = ((Element) node).getAttributeValue("class");
            try {
                Parser parser =
                        (Parser) Class.forName(className).newInstance();
                for (Object child : ((Element) node).getChildren("mime")) {
                    parsers.put(((Element) child).getTextTrim(), parser);
                }
            } catch (Exception e) {
                throw new JDOMException(
                        "Invalid parser configuration: " + className, e);
            }
        }
    }

    /**
     * Returns the parser instance configured for the given MIME type.
     * Returns <code>null</code> if the given MIME type is unknown.
     *
     * @param mimeType MIME type
     * @return configured Parser instance, or <code>null</code>
     */
    public Parser getParser(String mimeType) {
        return parsers.get(mimeType);
    }
    
    public MimeTypes getMimeRepository(){
        return mimeTypes;
    }
    
    /**
     * Provides a default configuration (TikaConfig).  Currently creates a
     * new instance each time it's called; we may be able to have it
     * return a shared instance once it is completely immutable.
     *
     * @return
     * @throws IOException
     * @throws JDOMException
     */
    public static TikaConfig getDefaultConfig()
            throws IOException, JDOMException {

        return new TikaConfig(
                TikaConfig.class.getResourceAsStream(DEFAULT_CONFIG_LOCATION));
    }

}
