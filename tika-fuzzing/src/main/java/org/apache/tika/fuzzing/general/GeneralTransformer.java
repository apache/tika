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
package org.apache.tika.fuzzing.general;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.fuzzing.Transformer;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class GeneralTransformer implements Transformer {

    private static final Logger LOG = LoggerFactory.getLogger(GeneralTransformer.class);

    Random random = new Random();

    private final int maxTransforms;
    private final Transformer[] transformers;
    private final Set<MediaType> supportedTypes;
    public GeneralTransformer() {
        this(new ByteDeleter(), new ByteFlipper(),
                new ByteInjector(), new Truncator(), new SpanSwapper());
    }

    public GeneralTransformer(Transformer ... transformers) {
        this(transformers.length, transformers);
    }

    public GeneralTransformer(int maxTransforms, Transformer ... transformers) {
        this.maxTransforms = (maxTransforms < 0) ? transformers.length : maxTransforms;
        this.transformers = transformers;
        Set<MediaType> tmpTypes = new HashSet<>();
        for (Transformer transformer : transformers) {
            tmpTypes.addAll(transformer.getSupportedTypes());
        }
        supportedTypes = Collections.unmodifiableSet(tmpTypes);
    }

    @Override
    public Set<MediaType> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public void transform(InputStream is, OutputStream os) throws IOException, TikaException {
        //used for debugging
        if (maxTransforms == 0) {
            return;
        }
        int transformerCount = (maxTransforms == 1) ? 1 : 1 + random.nextInt(maxTransforms);
        int[] transformerIndices = new int[transformerCount];
        for (int i = 0; i < transformerCount; i++) {
            transformerIndices[i] = random.nextInt(transformerCount);
        }
        //TODO -- make this actually streaming
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(is, bos);
        for (int i = 0; i < transformerIndices.length-1; i++) {
            byte[] bytes = bos.toByteArray();
            bos = new ByteArrayOutputStream();
            transformers[transformerIndices[i]].transform(
                    new ByteArrayInputStream(bytes), bos);
            bos.flush();
            if (bos.toByteArray().length == 0) {
                LOG.warn("zero length: "+transformers[transformerIndices[i]]);
            }
        }
        os.write(bos.toByteArray());
    }
}
