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
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.metadata.Metadata;

/**
 * Always returns the charset passed in via the initializer
 */
@TikaComponent(spi = false)
public class OverrideEncodingDetector implements EncodingDetector {

    public static Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * Configuration class for JSON deserialization.
     */
    public static class Config {
        public String charset = "UTF-8";
    }

    //would have preferred final, but need mutability for
    //loading via TikaConfig
    private final Charset charset;

    /**
     * Sets charset to UTF-8.
     */
    public OverrideEncodingDetector() {
        charset = DEFAULT_CHARSET;
    }

    /**
     * Constructor with explicit Config object.
     *
     * @param config the configuration
     */
    public OverrideEncodingDetector(Config config) {
        this.charset = Charset.forName(config.charset);
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public OverrideEncodingDetector(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

    /**
     * Constructor with explicit Charset object.
     *
     * @param charset the charset to always return
     */
    public OverrideEncodingDetector(Charset charset) {
        this.charset = charset;
    }

    @Override
    public Charset detect(InputStream input, Metadata metadata) throws IOException {
        return charset;
    }

    public Charset getCharset() {
        return charset;
    }
}
