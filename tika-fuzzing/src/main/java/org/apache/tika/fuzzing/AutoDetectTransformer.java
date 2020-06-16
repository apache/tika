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
package org.apache.tika.fuzzing;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.fuzzing.general.GeneralTransformer;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AutoDetectTransformer implements Transformer {

    private static final ServiceLoader DEFAULT_LOADER =
            new ServiceLoader(AutoDetectTransformer.class.getClassLoader());

    TikaConfig config = TikaConfig.getDefaultConfig();
    MediaTypeRegistry registry = config.getMediaTypeRegistry();
    Detector detector = TikaConfig.getDefaultConfig().getDetector();

    Transformer fallback = new GeneralTransformer();
    Map<MediaType, Transformer> transformerMap = new HashMap<>();

    public AutoDetectTransformer() {
        this(DEFAULT_LOADER.loadServiceProviders(org.apache.tika.fuzzing.Transformer.class));
    }

    public AutoDetectTransformer(List<Transformer> transformers) {
        for (Transformer t : transformers) {
            for (MediaType mediaType : t.getSupportedTypes()) {
                transformerMap.put(mediaType, t);
            }
        }
    }

    @Override
    public Set<MediaType> getSupportedTypes() {
        return transformerMap.keySet();
    }

    @Override
    public void transform(InputStream is, OutputStream os) throws IOException, TikaException {
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            // Automatically detect the MIME type of the document
            Metadata metadata = new Metadata();
            MediaType type = detector.detect(tis, metadata);
            Transformer transformer = getTransformer(type);
            transformer.transform(tis, os);
        }
    }

    private Transformer getTransformer(MediaType type) {
        if (type == null) {
            return fallback;
        }
        // We always work on the normalised, canonical form
        type = registry.normalize(type);

        while (type != null) {
            // Try finding a parser for the type
            Transformer transformer = transformerMap.get(type);
            if (transformer != null) {
                return transformer;
            }

            // Failing that, try for the parent of the type
            type = registry.getSupertype(type);
        }
        return fallback;
    }
}
