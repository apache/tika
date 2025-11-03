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
package org.apache.tika.pipes.core.async;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.plugins.PluginConfig;

public class MockFetcher implements Fetcher {

    private static final byte[] BYTES = ("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Nikolai Lobachevsky</metadata>" +
            "<write element=\"p\">main_content</write>" + "</mock>").getBytes(StandardCharsets.UTF_8);

    public MockFetcher() throws IOException {
        super();
    }


    @Override
    public InputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext) throws TikaException, IOException {
        return new ByteArrayInputStream(BYTES);
    }

    @Override
    public PluginConfig getPluginConfig() {
        return null;
    }
}
