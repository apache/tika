/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package org.apache.tika.server.config;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.digestutils.BouncyCastleDigester;
import org.apache.tika.parser.digestutils.CommonsDigester;

@Configuration
public class TikaConfigLoader {
    private static final Logger LOG = LoggerFactory.getLogger(TikaConfigLoader.class);
    private static final int DEFAULT_DIGEST_MARK_LIMIT = 20 * 1024 * 1024;

    private final Environment environment;

    @Autowired
    public TikaConfigLoader(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public TikaConfig tikaConfig() throws TikaException {
        String tikaConfig = environment.getProperty("tika.config");
        if (StringUtils.isNotBlank(tikaConfig)) {
            try {
                return new TikaConfig(getClass().getClassLoader().getResourceAsStream(tikaConfig));
            } catch (Exception e) {
                throw new TikaException("Could not load tika.config profile", e);
            }
        }
        return TikaConfig.getDefaultConfig();
    }

    @Bean
    public DigestingParser.Digester digester() {
        String digestConfig = environment.getProperty("tika.server.digest", "");
        
        if (StringUtils.isBlank(digestConfig)) {
            LOG.info("No digest configuration found, digester will not be enabled");
            return null;
        }

        int digestMarkLimit = environment.getProperty("tika.server.digestMarkLimit", 
                Integer.class, DEFAULT_DIGEST_MARK_LIMIT);

        try {
            // Try CommonsDigester first
            return new CommonsDigester(digestMarkLimit, digestConfig);
        } catch (IllegalArgumentException commonsException) {
            try {
                // Fall back to BouncyCastleDigester
                return new BouncyCastleDigester(digestMarkLimit, digestConfig);
            } catch (IllegalArgumentException bcException) {
                throw new IllegalArgumentException(
                        "Tried both CommonsDigester (" + commonsException.getMessage() + 
                        ") and BouncyCastleDigester (" + bcException.getMessage() + ")", 
                        bcException);
            }
        }
    }

    @Bean
    public Parser parser() throws TikaException {
        TikaConfig tikaConfig = tikaConfig();
        Parser parser = new AutoDetectParser(tikaConfig);

        DigestingParser.Digester digester = digester();
        if (digester != null) {
            boolean skipContainer = false;
            if (tikaConfig.getAutoDetectParserConfig().getDigesterFactory() != null && 
                tikaConfig.getAutoDetectParserConfig().getDigesterFactory().isSkipContainerDocument()) {
                skipContainer = true;
            }
            LOG.info("Wrapping parser with DigestingParser, skipContainer: {}", skipContainer);
            return new DigestingParser(parser, digester, skipContainer);
        }
        
        LOG.info("Using AutoDetectParser without digester");
        return parser;
    }
}
