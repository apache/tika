/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright owlocationNameEntitieship.
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
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ner.opennlp.OpenNLPNERecogniser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * This implementation of {@link org.apache.tika.parser.Parser} extracts entity names from text content and adds it to
 * the metadata.
 * <p>All the metadata keys will have a common suffix {@value #MD_KEY_PREFIX}</p>
 * <p>The Named Entity recogniser implementation can be changed by setting the system property {@value #SYS_PROP_NER_IMPL}
 * value to a name of class that implements {@link NERecogniser} contract</p>
 * @see OpenNLPNERecogniser
 */
public class NamedEntityParser extends AbstractParser {

    public static final Logger LOG = LoggerFactory.getLogger(NamedEntityParser.class);
    public static final Set<MediaType> MEDIA_TYPES = new HashSet<MediaType>();
    public static final String MD_KEY_PREFIX = "NER_";
    public static final String DEFAULT_NER_IMPL = OpenNLPNERecogniser.class.getName();
    public static final String SYS_PROP_NER_IMPL = "ner.impl.class";

    static {
        MEDIA_TYPES.add(MediaType.TEXT_PLAIN);
    }

    private NERecogniser recogniser;
    private volatile boolean initialized = false;
    private volatile boolean available = false;

    private synchronized void initialize(ParseContext context) {
        if (initialized) {
            return;
        }
        initialized = true;

        //TODO: read class name from context or config
        String className = System.getProperty(SYS_PROP_NER_IMPL, DEFAULT_NER_IMPL);
        LOG.info("going to load, instantiate and bind the instance of {}", className);
        try {
            recogniser = (NERecogniser) Class.forName(className).newInstance();
            this.available = recogniser.isAvailable();
        } catch (Exception e) {
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

        if (!MediaType.TEXT_PLAIN.toString().equals(metadata.get(Metadata.CONTENT_TYPE))) {
            //TODO: get text content from stream
            LOG.warn("Transform Content type {} to text/plain for better results",
                    metadata.get(Metadata.CONTENT_TYPE));
        }
        if (!initialized) {
            initialize(parseContext);
        }
        if (!available) {
            return;
        }

        String text = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        Map<String, Set<String>> names = recogniser.recognise(text);

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
