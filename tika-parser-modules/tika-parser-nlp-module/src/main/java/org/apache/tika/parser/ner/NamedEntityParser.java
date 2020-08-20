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

package org.apache.tika.parser.ner;

import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ner.opennlp.OpenNLPNERecogniser;
import org.apache.tika.parser.ner.regex.RegexNERecogniser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * This implementation of {@link org.apache.tika.parser.Parser} extracts
 * entity names from text content and adds it to the metadata.
 * <p>All the metadata keys will have a common suffix {@value #MD_KEY_PREFIX}</p>
 * <p>The Named Entity recogniser implementation can be changed by setting the
 * system property {@value #SYS_PROP_NER_IMPL} value to a name of class that
 * implements {@link NERecogniser} contract</p>
 * @see OpenNLPNERecogniser
 * @see NERecogniser
 *
 */
public class NamedEntityParser extends AbstractParser {
    public static final Logger LOG = LoggerFactory.getLogger(NamedEntityParser.class);
    public static final Set<MediaType> MEDIA_TYPES = new HashSet<>();
    public static final String MD_KEY_PREFIX = "NER_";
    public static final String DEFAULT_NER_IMPL =
            OpenNLPNERecogniser.class.getName() + "," + RegexNERecogniser.class.getName();
    public static final String SYS_PROP_NER_IMPL = "ner.impl.class";

    public Tika secondaryParser;

    static {
        MEDIA_TYPES.add(MediaType.TEXT_PLAIN);
    }

    private List<NERecogniser> nerChain;
    private volatile boolean initialized = false;
    private volatile boolean available = false;

    private synchronized void initialize(ParseContext context) {
        if (initialized) {
            return;
        }
        initialized = true;

        //TODO: read class name from context or config
        //There can be multiple classes in the form of comma separated class names;
        String classNamesString = System.getProperty(SYS_PROP_NER_IMPL,
                DEFAULT_NER_IMPL);
        String[] classNames = classNamesString.split(",");
        this.nerChain = new ArrayList<>(classNames.length);
        for (String className : classNames) {
            className = className.trim();
            LOG.info("going to load, instantiate and bind the instance of {}",
                    className);
            try {
                NERecogniser recogniser =
                        (NERecogniser) Class.forName(className).newInstance();
                LOG.info("{} is available ? {}", className,
                        recogniser.isAvailable());
                if (recogniser.isAvailable()) {
                    nerChain.add(recogniser);
                }
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        }
        try {
            TikaConfig config = new TikaConfig();
            this.secondaryParser = new Tika(config);
            this.available = !nerChain.isEmpty();
            LOG.info("Number of NERecognisers in chain {}", nerChain.size());
        } catch (Exception e){
            LOG.error(e.getMessage(), e);
            this.available = false;
        }
    }

    public Set<MediaType> getSupportedTypes(ParseContext parseContext) {
        return MEDIA_TYPES;
    }

    public void parse(InputStream inputStream, ContentHandler contentHandler,
                      Metadata metadata, ParseContext parseContext)
            throws IOException, SAXException, TikaException {

        if (!initialized) {
            initialize(parseContext);
        }
        if (!available) {
            return;
        }

        Reader reader = MediaType.TEXT_PLAIN.toString()
                .equals(metadata.get(Metadata.CONTENT_TYPE))
                ? new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                : secondaryParser.parse(inputStream);

        String text = IOUtils.toString(reader);
        IOUtils.closeQuietly(reader);

        for (NERecogniser ner : nerChain) {
            Map<String, Set<String>> names = ner.recognise(text);
            if (names != null) {
                for (Map.Entry<String, Set<String>> entry : names.entrySet()) {
                    if (entry.getValue() != null) {
                        String mdKey = MD_KEY_PREFIX + entry.getKey();
                        for (String name : entry.getValue()) {
                            metadata.add(mdKey, name);
                        }
                    }
                }
            }
        }
        XHTMLContentHandler xhtml = new XHTMLContentHandler(contentHandler, metadata);
        extractOutput(text.trim(), xhtml);
    }

    /**
     * writes the content to the given XHTML
     * content handler
     *
     * @param content
     *          the content which needs to be written
     * @param xhtml
     *          XHTML content handler
     * @throws SAXException
     *           if the XHTML SAX events could not be handled
     *
     */
    private void extractOutput(String content, XHTMLContentHandler xhtml) throws SAXException{
        xhtml.startDocument();
        xhtml.startElement("div");
        xhtml.characters(content);
        xhtml.endElement("div");
        xhtml.endDocument();
    }
}
