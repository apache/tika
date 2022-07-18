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
package org.apache.tika.pipes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;

public class PipesConfig extends PipesConfigBase {

    private static final Logger LOG = LoggerFactory.getLogger(PipesClient.class);

    private long maxWaitForClientMillis = 60000;

    public static PipesConfig load(Path tikaConfig) throws IOException, TikaConfigException {
        PipesConfig pipesConfig = new PipesConfig();
        try (InputStream is = Files.newInputStream(tikaConfig)) {
            Set<String> settings = pipesConfig.configure("pipes", is);
        }
        if (pipesConfig.getTikaConfig() == null) {
            LOG.debug("A separate tikaConfig was not specified in the <pipes/> element in the  " +
                    "config file; will use {} for pipes", tikaConfig);
            pipesConfig.setTikaConfig(tikaConfig);
        }
        return pipesConfig;
    }

    private PipesConfig() {

    }

    public long getMaxWaitForClientMillis() {
        return maxWaitForClientMillis;
    }

    public void setMaxWaitForClientMillis(long maxWaitForClientMillis) {
        this.maxWaitForClientMillis = maxWaitForClientMillis;
    }
}
