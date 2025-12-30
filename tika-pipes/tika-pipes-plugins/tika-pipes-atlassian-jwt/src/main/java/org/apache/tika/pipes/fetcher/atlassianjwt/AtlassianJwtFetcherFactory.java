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
package org.apache.tika.pipes.fetcher.atlassianjwt;

import java.io.IOException;

import org.pf4j.Extension;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.FetcherFactory;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Factory for creating Atlassian JWT fetchers.
 *
 * <p>Example JSON configuration:
 * <pre>
 * "fetchers": {
 *   "atlassian-jwt-fetcher": {
 *     "my-atlassian-fetcher": {
 *       "sharedSecret": "your-shared-secret",
 *       "issuer": "your-app-key",
 *       "connectTimeout": 30000,
 *       "socketTimeout": 120000
 *     }
 *   }
 * }
 * </pre>
 */
@Extension
public class AtlassianJwtFetcherFactory implements FetcherFactory {
    private static final String NAME = "atlassian-jwt-fetcher";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Fetcher buildExtension(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        return AtlassianJwtFetcher.build(extensionConfig);
    }
}
