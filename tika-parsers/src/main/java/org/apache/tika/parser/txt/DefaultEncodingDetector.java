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
package org.apache.tika.parser.txt;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class DefaultEncodingDetector implements EncodingDetector {

    public static final EncodingDetector INSTANCE =
            new DefaultEncodingDetector(new ServiceLoader(
                    DefaultEncodingDetector.class.getClassLoader()));

    private final ServiceLoader loader;

    public DefaultEncodingDetector(ServiceLoader loader) {
        this.loader = loader;
    }

    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {
        for (EncodingDetector detector
                : loader.loadServiceProviders(EncodingDetector.class)) {
            MediaType type = detector.detect(input, metadata);
            if (!MediaType.OCTET_STREAM.equals(type)) {
                return type;
            }
        }
        return MediaType.OCTET_STREAM;
    }

}
