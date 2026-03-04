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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import javax.imageio.spi.ServiceRegistry;

import org.apache.tika.config.ServiceLoader;

/**
 * A composite encoding detector based on all the {@link EncodingDetector}
 * implementations available through the
 * {@link ServiceRegistry service provider mechanism}.
 *
 * <p>Loaded detectors are sorted in two tiers:
 * <ol>
 *   <li>Base detectors (non-{@link MetaEncodingDetector}) sorted by full
 *       class name (non-Tika before Tika, then ascending alphabetically).
 *       The package ordering guarantees:
 *       {@code org.apache.tika.detect.WideUnicodeDetector} →
 *       {@code org.apache.tika.ml.*} (Mojibuster) →
 *       {@code org.apache.tika.parser.*} (HTML).</li>
 *   <li>{@link MetaEncodingDetector} instances always run last, after all
 *       base detectors have collected their candidates into
 *       {@link EncodingDetectorContext}.</li>
 * </ol></p>
 *
 * <p>If you need to control the order of the Detectors explicitly, construct
 * your own {@link CompositeEncodingDetector} and pass in the list in the
 * required order.</p>
 *
 * <p>{@link MetaEncodingDetector} handling (collect-all-then-arbitrate)
 * is provided by {@link CompositeEncodingDetector}.</p>
 *
 * @since Apache Tika 1.15
 */
public class DefaultEncodingDetector extends CompositeEncodingDetector {

    public DefaultEncodingDetector() {
        this(new ServiceLoader(DefaultEncodingDetector.class.getClassLoader()));
    }

    public DefaultEncodingDetector(ServiceLoader loader) {
        super(sorted(loader.loadServiceProviders(EncodingDetector.class)));
    }

    public DefaultEncodingDetector(ServiceLoader loader,
                                   Collection<Class<? extends EncodingDetector>>
                                           excludeEncodingDetectors) {
        super(sorted(loader.loadServiceProviders(EncodingDetector.class)),
                excludeEncodingDetectors);
    }

    private static List<EncodingDetector> sorted(List<EncodingDetector> detectors) {
        // Two-key sort: base detectors first (meta=0) then MetaEncodingDetectors (meta=1),
        // within each tier sorted by full class name for stability across JARs.
        detectors.sort(Comparator
                .<EncodingDetector, Integer>comparing(
                        d -> (d instanceof MetaEncodingDetector) ? 1 : 0)
                .thenComparing(d -> d.getClass().getName()));
        return detectors;
    }
}
