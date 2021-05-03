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
package org.apache.tika.parser.ctakes;

import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;

/**
 * CTAKESParser decorates a {@link Parser} and leverages on
 * {@link CTAKESContentHandler} to extract biomedical information from
 * clinical text using Apache cTAKES.
 * <p>It is normally called by supplying an instance to
 * {@link AutoDetectParser}, such as:
 * <code>AutoDetectParser parser = new AutoDetectParser(new CTAKESParser());</code>
 * <p>It can also be used by giving a Tika Config file similar to:
 * <code>
 * <properties>
 * <parsers>
 * <parser class="org.apache.tika.parser.ctakes.CTAKESParser">
 * <parser class="org.apache.tika.parser.DefaultParser"/>
 * </parser>
 * </parsers>
 * </properties>
 * </code>
 * <p>Because this is a Parser Decorator, and not a normal Parser in
 * it's own right, it isn't normally selected via the Parser Service Loader.
 */
public class CTAKESParser extends ParserDecorator {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -2313482748027097961L;

    /**
     * Wraps the default Parser
     */
    public CTAKESParser() {
        this(TikaConfig.getDefaultConfig());
    }

    /**
     * Wraps the default Parser for this Config
     */
    public CTAKESParser(TikaConfig config) {
        this(config.getParser());
    }

    /**
     * Wraps the specified Parser
     */
    public CTAKESParser(Parser parser) {
        super(parser);
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        CTAKESConfig config = context.get(CTAKESConfig.class, new CTAKESConfig());
        CTAKESContentHandler ctakesHandler = new CTAKESContentHandler(handler, metadata, config);
        super.parse(stream, ctakesHandler, metadata, context);
    }

    //@Override
    public String getDecorationName() {
        return "CTakes";
    }
}
