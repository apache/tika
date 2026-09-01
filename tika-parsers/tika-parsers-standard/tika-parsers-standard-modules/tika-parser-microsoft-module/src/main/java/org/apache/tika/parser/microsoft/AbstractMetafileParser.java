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
package org.apache.tika.parser.microsoft;

import java.io.IOException;

import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RenderingParser;
import org.apache.tika.renderer.Renderer;

/**
 * What {@link EMFParser} and {@link WMFParser} share: the configuration of
 * the metafile parsers and the renderer they may be handed.
 */
abstract class AbstractMetafileParser implements Parser, RenderingParser {

    private final MetafileParserConfig defaultConfig;
    private Renderer renderer;

    AbstractMetafileParser(MetafileParserConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    /**
     * The component name this parser reads its configuration from,
     * {@code emf-parser} or {@code wmf-parser}.
     */
    abstract String componentName();

    MetafileParserConfig getConfig(ParseContext context) throws TikaException, IOException {
        return ParseContextConfig.getConfig(context, componentName(), MetafileParserConfig.class,
                defaultConfig);
    }

    /**
     * Spools the stream when the image is going to be rendered, so the
     * renderer can read the metafile itself rather than only the parsed
     * picture.
     */
    static void prepareForRendering(TikaInputStream tis, MetafileParserConfig config,
                                    Metadata metadata) throws IOException {
        if (config.shouldRender(metadata)) {
            tis.getFile();
        }
    }

    Renderer getRenderer() {
        return renderer;
    }

    @Override
    public void setRenderer(Renderer renderer) {
        this.renderer = renderer;
    }
}
