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

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

@TikaComponent(spi = false)
public class UniversalEncodingDetector implements EncodingDetector {

    private static final int BUFSIZE = 1024;

    private static final int DEFAULT_MARK_LIMIT = 16 * BUFSIZE;

    /**
     * Configuration class for JSON deserialization.
     */
    public static class Config implements Serializable {
        public int markLimit = DEFAULT_MARK_LIMIT;
    }

    private int markLimit = DEFAULT_MARK_LIMIT;

    /**
     * Default constructor for SPI loading.
     */
    public UniversalEncodingDetector() {
    }

    /**
     * Constructor with explicit Config object.
     *
     * @param config the configuration
     */
    public UniversalEncodingDetector(Config config) {
        this.markLimit = config.markLimit;
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public UniversalEncodingDetector(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

    public Charset detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext) throws IOException {
        if (tis == null) {
            return null;
        }

        tis.mark(markLimit);
        try {
            UniversalEncodingListener listener = new UniversalEncodingListener(metadata);

            byte[] b = new byte[BUFSIZE];
            int n = 0;
            int m = tis.read(b);
            while (m != -1 && n < markLimit && !listener.isDone()) {
                n += m;
                listener.handleData(b, 0, m);
                m = tis.read(b, 0, Math.min(b.length, markLimit - n));
            }

            return listener.dataEnd();
        } catch (LinkageError e) {
            return null; // juniversalchardet is not available
        } finally {
            tis.reset();
        }
    }

    public int getMarkLimit() {
        return markLimit;
    }

    /**
     * How far into the stream to read for charset detection.
     * Default is 8192.
     *
     * @param markLimit
     */
    public void setMarkLimit(int markLimit) {
        this.markLimit = markLimit;
    }
}
