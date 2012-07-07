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
import java.nio.charset.Charset;
import java.util.List;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.CharsetUtils;

public class DefaultEncodingDetector implements EncodingDetector {

    public static final EncodingDetector INSTANCE =
            new DefaultEncodingDetector(new ServiceLoader(
                    DefaultEncodingDetector.class.getClassLoader()));

    private final List<EncodingDetector> detectors;

    public DefaultEncodingDetector(ServiceLoader loader) {
        this.detectors =
                loader.loadStaticServiceProviders(EncodingDetector.class);
    }

    public Charset detect(InputStream input, Metadata metadata)
            throws IOException {
        // Check all available detectors
        for (EncodingDetector detector : detectors) {
            Charset charset = detector.detect(input, metadata);
            if (charset != null) {
                return charset;
            }
        }

        // Try determining the charset based on document metadata
        MediaType type = MediaType.parse(metadata.get(Metadata.CONTENT_TYPE));
        if (type != null) {
            String charset = type.getParameters().get("charset");
            if (charset != null) {
                try {
                    return CharsetUtils.forName(charset);
                } catch (IllegalArgumentException e) {
                    // ignore
                }
            }
        }

        return null;
    }

}
