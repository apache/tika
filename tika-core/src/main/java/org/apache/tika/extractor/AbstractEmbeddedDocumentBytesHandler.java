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
package org.apache.tika.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.utils.StringUtils;

public abstract class AbstractEmbeddedDocumentBytesHandler implements EmbeddedDocumentBytesHandler {

    List<Integer> ids = new ArrayList<>();

    public String getEmitKey(
            String containerEmitKey,
            int embeddedId,
            EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig,
            Metadata metadata) {
        String embeddedIdString =
                embeddedDocumentBytesConfig.getZeroPadName() > 0
                        ? StringUtils.leftPad(
                                Integer.toString(embeddedId),
                                embeddedDocumentBytesConfig.getZeroPadName(),
                                "0")
                        : Integer.toString(embeddedId);

        StringBuilder emitKey =
                new StringBuilder(containerEmitKey)
                        .append("/")
                        .append(FilenameUtils.getName(containerEmitKey))
                        .append(embeddedDocumentBytesConfig.getEmbeddedIdPrefix())
                        .append(embeddedIdString);

        if (embeddedDocumentBytesConfig
                .getSuffixStrategy()
                .equals(EmbeddedDocumentBytesConfig.SUFFIX_STRATEGY.EXISTING)) {
            String fName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            String suffix = FilenameUtils.getSuffixFromPath(fName);
            suffix = suffix.toLowerCase(Locale.US);
            emitKey.append(suffix);
        }
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
}
