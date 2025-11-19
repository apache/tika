/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.tika.extractor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.ServiceLoaderUtils;

/**
 * Loads EmbeddedStreamTranslators via service loading. Tries to run each in turn. If a translator
 * accepts the stream, it will do the translation but not close the stream.
 */
public class DefaultEmbeddedStreamTranslator implements EmbeddedStreamTranslator {

    final List<EmbeddedStreamTranslator> translators;

    private static List<EmbeddedStreamTranslator> getDefaultFilters(ServiceLoader loader) {
        List<EmbeddedStreamTranslator> embeddedStreamTranslators =
                        loader.loadServiceProviders(EmbeddedStreamTranslator.class);
        ServiceLoaderUtils.sortLoadedClasses(embeddedStreamTranslators);
        return embeddedStreamTranslators;
    }

    public DefaultEmbeddedStreamTranslator() {
        this(getDefaultFilters(new ServiceLoader()));
    }

    private DefaultEmbeddedStreamTranslator(List<EmbeddedStreamTranslator> translators) {
        this.translators = translators;
    }

    /**
     * This should sniff the stream to determine if it needs to be translated. The translator is
     * responsible for resetting the stream if any bytes have been read.
     * 
     * @param inputStream
     * @param metadata
     * @return
     * @throws IOException
     */
    @Override
    public boolean shouldTranslate(TikaInputStream inputStream, Metadata metadata)
                    throws IOException {
        for (EmbeddedStreamTranslator translator : translators) {
            if (translator.shouldTranslate(inputStream, metadata)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This will consume the InputStream and write the stream to the output stream
     * 
     * @param inputStream
     * @param metadata
     * @param outputStream to write to
     * @return
     * @throws IOException
     */
    @Override
    public void translate(TikaInputStream inputStream, Metadata metadata, OutputStream outputStream)
                    throws IOException {
        for (EmbeddedStreamTranslator translator : translators) {
            if (translator.shouldTranslate(inputStream, metadata)) {
                translator.translate(inputStream, metadata, outputStream);
                return;
            }
        }
    }
}
