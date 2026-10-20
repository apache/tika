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
package org.apache.tika.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ConfigReaderLookup {

    private ConfigReaderLookup() {
    }

    static ConfigReader find() {
        // OSGi services come and go, so they are looked up per call; the classpath is fixed
        List<ConfigReader> readers =
                new ServiceLoader().loadDynamicServiceProviders(ConfigReader.class);
        return only(readers.isEmpty() ? Classpath.READERS : distinct(readers));
    }

    private static final class Classpath {
        private static final List<ConfigReader> READERS =
                distinct(new ServiceLoader().loadStaticServiceProviders(ConfigReader.class));
    }

    private static List<ConfigReader> distinct(List<ConfigReader> readers) {
        Map<String, ConfigReader> byClass = new LinkedHashMap<>();
        for (ConfigReader reader : readers) {
            byClass.putIfAbsent(reader.getClass().getName(), reader);
        }
        return new ArrayList<>(byClass.values());
    }

    // the reader enforces OperatorOnly, so which one runs must never depend on discovery order
    private static ConfigReader only(List<ConfigReader> readers) {
        if (readers.size() > 1) {
            List<String> names = new ArrayList<>();
            for (ConfigReader reader : readers) {
                names.add(reader.getClass().getName());
            }
            throw new IllegalStateException("Expected one " + ConfigReader.class.getName() +
                    ", found " + names);
        }
        return readers.isEmpty() ? null : readers.get(0);
    }
}
