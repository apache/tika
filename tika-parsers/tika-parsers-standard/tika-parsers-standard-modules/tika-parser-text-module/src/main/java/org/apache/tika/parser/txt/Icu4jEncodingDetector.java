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
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.CharsetUtils;

@TikaComponent(spi = false, name = "icu4j-encoding-detector")
public class Icu4jEncodingDetector implements EncodingDetector {

    /**
     * Configuration class for JSON deserialization.
     */
    public static class Config implements Serializable {
        public boolean stripMarkup = false;
        public int markLimit = CharsetDetector.DEFAULT_MARK_LIMIT;
        public List<String> ignoreCharsets = new ArrayList<>();

        public boolean isStripMarkup() {
            return stripMarkup;
        }

        public void setStripMarkup(boolean stripMarkup) {
            this.stripMarkup = stripMarkup;
        }

        public int getMarkLimit() {
            return markLimit;
        }

        public void setMarkLimit(int markLimit) {
            this.markLimit = markLimit;
        }

        public List<String> getIgnoreCharsets() {
            return ignoreCharsets;
        }

        public void setIgnoreCharsets(List<String> ignoreCharsets) {
            this.ignoreCharsets = ignoreCharsets;
        }
    }

    private final Config defaultConfig;
    /**
     * Default constructor for SPI loading.
     */
    public Icu4jEncodingDetector() {
        defaultConfig = new Config();
    }

    /**
     * Constructor with explicit Config object.
     *
     * @param config the configuration
     */
    public Icu4jEncodingDetector(Config config) {
        defaultConfig = config;
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public Icu4jEncodingDetector(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }


    @Override
    public Charset detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext) throws IOException {
        if (tis == null) {
            return null;
        }
        //TODO: add runtime updates?
        Config config = defaultConfig;

        CharsetDetector detector = new CharsetDetector(config.markLimit);

        String incomingCharset = metadata.get(Metadata.CONTENT_ENCODING);
        String incomingType = metadata.get(Metadata.CONTENT_TYPE);
        if (incomingCharset == null && incomingType != null) {
            // TIKA-341: Use charset in content-type
            MediaType mt = MediaType.parse(incomingType);
            if (mt != null) {
                incomingCharset = mt.getParameters().get("charset");
            }
        }

        if (incomingCharset != null) {
            String cleaned = CharsetUtils.clean(incomingCharset);
            if (cleaned != null) {
                detector.setDeclaredEncoding(cleaned);
            } else {
                // TODO: log a warning?
            }
        }

        // TIKA-341 without enabling input filtering (stripping of tags)
        // short HTML tests don't work well
        detector.enableInputFilter(true);

        detector.setText(tis);

        for (CharsetMatch match : detector.detectAll()) {
            try {
                String n = match.getNormalizedName();
                if (config.ignoreCharsets.contains(n)) {
                    continue;
                }
                return CharsetUtils.forName(match.getNormalizedName());
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }

        return null;
    }

    public Config getDefaultConfig() {
        return defaultConfig;
    }

}
