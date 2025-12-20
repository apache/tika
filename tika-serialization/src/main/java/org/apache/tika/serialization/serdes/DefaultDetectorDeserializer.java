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
package org.apache.tika.serialization.serdes;

import java.util.Collection;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.mime.MimeTypes;

/**
 * Deserializer for DefaultDetector that handles exclusions.
 * <p>
 * Supports JSON formats:
 * <pre>
 * "default-detector"
 * { "default-detector": {} }
 * { "default-detector": { "exclude": ["html-detector", "zip-detector"] } }
 * </pre>
 */
public class DefaultDetectorDeserializer extends SpiCompositeDeserializer<DefaultDetector> {

    @Override
    @SuppressWarnings("unchecked")
    protected DefaultDetector createInstance(Collection<Class<?>> excludedClasses) {
        if (excludedClasses == null || excludedClasses.isEmpty()) {
            return new DefaultDetector();
        }
        return new DefaultDetector(MimeTypes.getDefaultMimeTypes(),
                new ServiceLoader(),
                (Collection<Class<? extends Detector>>) (Collection<?>) excludedClasses);
    }
}
