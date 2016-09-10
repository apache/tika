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
package org.apache.tika.osgi.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.tika.Tika;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.osgi.TikaService;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class TikaServiceImpl implements TikaService {

    private static final long serialVersionUID = 6587317333752670909L;

    private final Parser parser;
    
    private final Detector detector;
    
    private final Translator translator;
    
    private final ServiceLoader loader;

    public TikaServiceImpl(TikaConfig config) {
        
        
        if(config == null)
        {
            config = TikaConfig.getDefaultConfig();
        }
        Tika tika = new Tika(config);
        this.parser = tika.getParser();
        this.detector = tika.getDetector();
        this.translator = tika.getTranslator();
        this.loader = config.getServiceLoader();
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return this.parser.getSupportedTypes(context);
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        this.parser.parse(stream, handler, metadata, context);

    }

    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        return this.detector.detect(input, metadata);
    }
    
    @Override
    public String translate(String text, String sourceLanguage, String targetLanguage)
            throws TikaException, IOException {
        return this.translator.translate(text, sourceLanguage, targetLanguage);
    }
    
    @Override
    public String translate(String text, String targetLanguage) throws TikaException, IOException {
        return this.translator.translate(text, targetLanguage);
    }
    
    @Override
    public boolean isAvailable() {
        return this.translator.isAvailable();
    }
    
    @Override
    public Detector getWrappedDetector() {
        return this.detector;
    }
    
    @Override
    public Parser getWrappedParser() {
        return this.parser;
    }
    
    public ServiceLoader getServiceLoader() {
        return this.loader;
    }

}
