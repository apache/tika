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

package org.apache.tika.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;

/**
 * Factory for an AutoDetectParser
 */
public class AutoDetectParserFactory extends ParserFactory {

    /**
     * Path to a tika-config file.  This must be a literal
     * file or findable on the classpath.
     */
    public static final String TIKA_CONFIG_PATH = "tika_config_path";

    public AutoDetectParserFactory(Map<String, String> args) {
        super(args);
    }

    @Override
    public Parser build() throws IOException, SAXException, TikaException {
        String tikaConfigPath = args.remove(TIKA_CONFIG_PATH);
        TikaConfig tikaConfig = null;
        if (tikaConfigPath != null) {
            if (Files.isReadable(Paths.get(tikaConfigPath))) {
                tikaConfig = new TikaConfig(Paths.get(tikaConfigPath));
            } else if (this.getClass().getResource(tikaConfigPath) != null) {
                try (InputStream is = this.getClass().getResourceAsStream(tikaConfigPath)) {
                    tikaConfig = new TikaConfig(is);
                }
            }
        }
        if (tikaConfig == null) {
            tikaConfig = TikaConfig.getDefaultConfig();
        }
        return new AutoDetectParser(tikaConfig);
    }
}
