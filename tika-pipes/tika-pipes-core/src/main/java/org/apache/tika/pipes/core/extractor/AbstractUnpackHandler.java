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
package org.apache.tika.pipes.core.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.tika.extractor.UnpackHandler;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.StringUtils;

public abstract class AbstractUnpackHandler implements UnpackHandler {

    List<Integer> ids = new ArrayList<>();

    public String getEmitKey(String containerEmitKey, int embeddedId,
                             UnpackConfig unpackConfig,
                             Metadata metadata) {
        String embeddedIdString = unpackConfig.getZeroPadName() > 0 ?
                StringUtils.leftPad(Integer.toString(embeddedId),
                        unpackConfig.getZeroPadName(), "0") :
                Integer.toString(embeddedId);

        StringBuilder emitKey = new StringBuilder();
        if (unpackConfig.getKeyBaseStrategy() == UnpackConfig.KEY_BASE_STRATEGY.DEFAULT) {
            // Default pattern: {containerKey}-embed/{id}{suffix}
            emitKey.append(containerEmitKey);
            emitKey.append("-embed/");
            emitKey.append(embeddedIdString);
        } else {
            // CUSTOM: use the configured emitKeyBase
            emitKey.append(unpackConfig.getEmitKeyBase());
            emitKey.append(unpackConfig.getEmbeddedIdPrefix());
            emitKey.append(embeddedIdString);
        }
        appendSuffix(emitKey, metadata, unpackConfig);
        return emitKey.toString();
    }

    @Override
    public void add(int id, Metadata metadata, InputStream bytes) throws IOException {
        ids.add(id);
    }

    @Override
    public List<Integer> getIds() {
        return ids;
    }

    private void appendSuffix(StringBuilder emitKey, Metadata metadata, UnpackConfig unpackConfig) {
        if (unpackConfig.getSuffixStrategy().equals(
                UnpackConfig.SUFFIX_STRATEGY.EXISTING)) {
            String fName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            String suffix = FilenameUtils.getSuffixFromPath(fName);
            suffix = suffix.toLowerCase(Locale.US);
            emitKey.append(suffix);
        } else if (unpackConfig.getSuffixStrategy()
                                              .equals(UnpackConfig.SUFFIX_STRATEGY.DETECTED)) {
            emitKey.append(FilenameUtils.calculateExtension(metadata, ".bin"));
        }
    }
}
