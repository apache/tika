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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class RUnpackExtractorFactory implements EmbeddedDocumentByteStoreExtractorFactory {

    public static long DEFAULT_MAX_EMBEDDED_BYTES_FOR_EXTRACTION = 10l * 1024l * 1024l * 1024l;

    private boolean writeFileNameToContent = true;
    private Set<String> embeddedBytesIncludeMimeTypes = Collections.EMPTY_SET;
    private Set<String> embeddedBytesExcludeMimeTypes = Collections.EMPTY_SET;
    private Set<String> embeddedBytesIncludeEmbeddedResourceTypes = Collections.EMPTY_SET;
    private Set<String> embeddedBytesExcludeEmbeddedResourceTypes = Collections.EMPTY_SET;

    private long maxEmbeddedBytesForExtraction = DEFAULT_MAX_EMBEDDED_BYTES_FOR_EXTRACTION;
    @Field
    public void setWriteFileNameToContent(boolean writeFileNameToContent) {
        this.writeFileNameToContent = writeFileNameToContent;
    }

    @Field
    public void setEmbeddedBytesIncludeMimeTypes(List<String> includeMimeTypes) {
        embeddedBytesIncludeMimeTypes = new HashSet<>();
        embeddedBytesIncludeMimeTypes.addAll(includeMimeTypes);
    }

    @Field
    public void setEmbeddedBytesExcludeMimeTypes(List<String> excludeMimeTypes) {
        embeddedBytesExcludeMimeTypes = new HashSet<>();
        embeddedBytesExcludeMimeTypes.addAll(excludeMimeTypes);

    }

    @Field
    public void setEmbeddedBytesIncludeEmbeddedResourceTypes(List<String> includeAttachmentTypes) {
        embeddedBytesIncludeEmbeddedResourceTypes = new HashSet<>();
        embeddedBytesIncludeEmbeddedResourceTypes.addAll(includeAttachmentTypes);

    }

    @Field
    public void setEmbeddedBytesExcludeEmbeddedResourceTypes(List<String> excludeAttachmentTypes) {
        embeddedBytesExcludeEmbeddedResourceTypes = new HashSet<>();
        embeddedBytesExcludeEmbeddedResourceTypes.addAll(excludeAttachmentTypes);

    }

    /**
     * Total number of bytes to write out. A good zip bomb may contain petabytes
     * compressed into a few kb. Make sure that you can't fill up a disk!
     *
     * This does not include the container file in the count of bytes written out.
     * This only counts the lengths of the embedded files.
     *
     * @param maxEmbeddedBytesForExtraction
     */
    @Field
    public void setMaxEmbeddedBytesForExtraction(long maxEmbeddedBytesForExtraction) throws TikaConfigException {
        if (maxEmbeddedBytesForExtraction < 0) {
            throw new TikaConfigException("maxEmbeddedBytesForExtraction must be >= 0");
        }
        this.maxEmbeddedBytesForExtraction = maxEmbeddedBytesForExtraction;
    }

    @Override
    public EmbeddedDocumentExtractor newInstance(Metadata metadata, ParseContext parseContext) {
        RUnpackExtractor ex =
                new RUnpackExtractor(parseContext,
                        maxEmbeddedBytesForExtraction);
        ex.setWriteFileNameToContent(writeFileNameToContent);
        ex.setEmbeddedBytesSelector(createEmbeddedBytesSelector());
        return ex;
    }


    private EmbeddedBytesSelector createEmbeddedBytesSelector() {
        if (embeddedBytesIncludeMimeTypes.size() == 0 &&
                embeddedBytesExcludeMimeTypes.size() == 0 &&
                embeddedBytesIncludeEmbeddedResourceTypes.size() == 0 &&
                embeddedBytesExcludeEmbeddedResourceTypes.size() == 0) {
            return EmbeddedBytesSelector.ACCEPT_ALL;
        }
        return new BasicEmbeddedBytesSelector(embeddedBytesIncludeMimeTypes,
                embeddedBytesExcludeMimeTypes, embeddedBytesIncludeEmbeddedResourceTypes,
                embeddedBytesExcludeEmbeddedResourceTypes);
    }
}
