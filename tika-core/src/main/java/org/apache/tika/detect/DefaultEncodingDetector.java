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
package org.apache.tika.detect;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import javax.imageio.spi.ServiceRegistry;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.textquality.TextQualityScorer;

/**
 * A composite encoding detector based on all the {@link EncodingDetector} implementations
 * available through the {@link ServiceRegistry service provider mechanism}.  Those
 * loaded via the service provider mechanism are ordered by how they appear in the
 * file, if there is a single service file.  If multiple, there is no guarantee of order.
 * <p>
 * <p>
 * If you need to control the order of the Detectors, you should instead
 * construct your own {@link CompositeDetector} and pass in the list
 * of Detectors in the required order.
 * <p>
 * When a real {@link TextQualityScorer} is on the classpath, this detector
 * delegates to {@link TextQualityEncodingDetector} for collect-all-then-arbitrate
 * behavior. Otherwise, it uses first-match-wins from {@link CompositeEncodingDetector}.
 *
 * @since Apache Tika 1.15
 */
public class DefaultEncodingDetector extends CompositeEncodingDetector {

    private final TextQualityEncodingDetector qualityDetector;

    public DefaultEncodingDetector() {
        this(new ServiceLoader(DefaultEncodingDetector.class.getClassLoader()));
    }

    public DefaultEncodingDetector(ServiceLoader loader) {
        super(loader.loadServiceProviders(EncodingDetector.class));
        this.qualityDetector = initQualityDetector();
    }

    public DefaultEncodingDetector(ServiceLoader loader,
                                   Collection<Class<? extends EncodingDetector>>
                                           excludeEncodingDetectors) {
        super(loader.loadServiceProviders(EncodingDetector.class), excludeEncodingDetectors);
        this.qualityDetector = initQualityDetector();
    }

    private TextQualityEncodingDetector initQualityDetector() {
        if (!TextQualityScorer.getScorers().isEmpty()) {
            return new TextQualityEncodingDetector(getDetectors());
        }
        return null;
    }

    @Override
    public Charset detect(TikaInputStream tis, Metadata metadata,
                          ParseContext parseContext) throws IOException {
        if (qualityDetector != null) {
            return qualityDetector.detect(tis, metadata, parseContext);
        }
        return super.detect(tis, metadata, parseContext);
    }
}
