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

import org.apache.tika.extractor.EmbeddedDocumentBytesHandler;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.StringUtils;

public abstract class AbstractEmbeddedDocumentBytesHandler implements EmbeddedDocumentBytesHandler {

    List<Integer> ids = new ArrayList<>();

    public String getEmitKey(String containerEmitKey, int embeddedId,
            EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig, Metadata metadata) {
        String embeddedIdString = embeddedDocumentBytesConfig.getZeroPadName() > 0
                ? StringUtils.leftPad(Integer.toString(embeddedId), embeddedDocumentBytesConfig.getZeroPadName(), "0")
                : Integer.toString(embeddedId);

        StringBuilder emitKey = new StringBuilder();
        if (embeddedDocumentBytesConfig
                .getKeyBaseStrategy() == EmbeddedDocumentBytesConfig.KEY_BASE_STRATEGY.CONTAINER_NAME_AS_IS) {
            emitKey.append(containerEmitKey);
            emitKey.append("-embed");
            emitKey.append("/");
            emitKey.append(embeddedIdString).append(embeddedDocumentBytesConfig.getEmbeddedIdPrefix());
            String fName = FilenameUtils.getSanitizedEmbeddedFileName(metadata, ".bin", 100);
            if (!StringUtils.isBlank(fName)) {
                emitKey.append(fName);
            }
            return emitKey.toString();
        } else if (embeddedDocumentBytesConfig
                .getKeyBaseStrategy() == EmbeddedDocumentBytesConfig.KEY_BASE_STRATEGY.CONTAINER_NAME_NUMBERED) {
            emitKey.append(containerEmitKey);
            emitKey.append("-embed");
            emitKey.append("/").append(FilenameUtils.getName(containerEmitKey));
        } else {
            emitKey.append(embeddedDocumentBytesConfig.getEmitKeyBase());
        }
        //at this point the emit key has the full "file" part, now we
        //add the embedded id prefix, the embedded id string and then maybe
        //the file extension
        emitKey.append(embeddedDocumentBytesConfig.getEmbeddedIdPrefix()).append(embeddedIdString);
        appendSuffix(emitKey, metadata, embeddedDocumentBytesConfig);
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

    private void appendSuffix(StringBuilder emitKey, Metadata metadata,
            EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig) {
        if (embeddedDocumentBytesConfig.getSuffixStrategy()
                .equals(EmbeddedDocumentBytesConfig.SUFFIX_STRATEGY.EXISTING)) {
            String fName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            String suffix = FilenameUtils.getSuffixFromPath(fName);
            suffix = suffix.toLowerCase(Locale.US);
            emitKey.append(suffix);
        } else if (embeddedDocumentBytesConfig.getSuffixStrategy()
                .equals(EmbeddedDocumentBytesConfig.SUFFIX_STRATEGY.DETECTED)) {
            emitKey.append(FilenameUtils.calculateExtension(metadata, ".bin"));
        }
    }
}
