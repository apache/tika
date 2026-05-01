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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.spi.ServiceRegistry;

import org.apache.tika.config.ServiceLoader;

/**
 * A composite encoding detector based on all the {@link EncodingDetector}
 * implementations available through the
 * {@link ServiceRegistry service provider mechanism}.
 *
 * <p>The default chain (Tika 3.x style) runs three detectors in order, with
 * the first non-empty result winning:
 * <ol>
 *   <li>{@code org.apache.tika.parser.html.HtmlEncodingDetector}</li>
 *   <li>{@code org.apache.tika.parser.txt.UniversalEncodingDetector}</li>
 *   <li>{@code org.apache.tika.parser.txt.Icu4jEncodingDetector}</li>
 * </ol>
 * Any other {@link EncodingDetector} discovered via SPI (e.g.,
 * user-supplied detectors) runs after the three blessed detectors,
 * preserving back-compat for callers who add their own.</p>
 *
 * <p>If you need to control the order of the Detectors explicitly, construct
 * your own {@link CompositeEncodingDetector} and pass in the list in the
 * required order.</p>
 *
 * @since Apache Tika 1.15
 */
public class DefaultEncodingDetector extends CompositeEncodingDetector {

    /** Pinned ordering for the 3.x-style default chain. Detectors not on this
     *  map keep their natural SPI load order behind the three blessed ones. */
    private static final Map<String, Integer> PRIORITY = buildPriority();

    private static Map<String, Integer> buildPriority() {
        Map<String, Integer> p = new HashMap<>();
        p.put("org.apache.tika.parser.html.HtmlEncodingDetector", 0);
        p.put("org.apache.tika.parser.txt.UniversalEncodingDetector", 1);
        p.put("org.apache.tika.parser.txt.Icu4jEncodingDetector", 2);
        return p;
    }

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
        // Pin the 3.x default chain (html, universal, icu4j) to fixed
        // positions; other detectors fall to the end with stable secondary
        // ordering by class name.
        detectors.sort(Comparator
                .<EncodingDetector, Integer>comparing(
                        d -> PRIORITY.getOrDefault(
                                d.getClass().getName(), Integer.MAX_VALUE))
                .thenComparing(d -> d.getClass().getName()));
        return detectors;
    }
}
