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
 */package org.apache.tika.renderer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.ServiceLoaderUtils;

public class CompositeRenderer implements Renderer, Initializable {

    private Map<MediaType, Renderer> rendererMap = new HashMap<>();

    public CompositeRenderer(ServiceLoader serviceLoader) {
        this(getDefaultRenderers(serviceLoader));
    }

    public CompositeRenderer(List<Renderer> renderers) {
        Map<MediaType, Renderer> tmp = new ConcurrentHashMap<>();
        ParseContext empty = new ParseContext();
        for (Renderer renderer : renderers) {
            for (MediaType mt : renderer.getSupportedTypes(empty)) {
                tmp.put(mt, renderer);
            }
        }
        rendererMap = Collections.unmodifiableMap(tmp);
    }
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return rendererMap.keySet();
    }

    @Override
    public RenderResults render(InputStream is, Metadata metadata, ParseContext parseContext,
                                RenderRequest... requests) throws IOException, TikaException {

        String mediaTypeString = metadata.get(TikaCoreProperties.TYPE);
        if (mediaTypeString == null) {
            throw new TikaException("need to specify file type in metadata");
        }
        MediaType mt = MediaType.parse(mediaTypeString);
        if (mt == null) {
            throw new TikaException("can't parse mediaType: " + mediaTypeString);
        }
        Renderer renderer = rendererMap.get(mt);
        if (renderer == null) {
            throw new TikaException("I regret I can't find a renderer for " + mt);
        }
        return renderer.render(is, metadata, parseContext, requests);
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {

    }

    private static List<Renderer> getDefaultRenderers(ServiceLoader loader) {
        List<Renderer> staticRenderers =
                loader.loadStaticServiceProviders(Renderer.class);

        ServiceLoaderUtils.sortLoadedClasses(staticRenderers);
        return staticRenderers;
    }
}
