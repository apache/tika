package org.apache.tika.batch.fs;

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.util.PropsUtil;

/**
 * Selector that chooses files based on their file name
 * and their size, as determined by Metadata.RESOURCE_NAME_KEY and Metadata.CONTENT_LENGTH.
 * <p/>
 * The {@link #excludeFileName} pattern is applied first (if it isn't null).
 * Then the {@link #includeFileName} pattern is applied (if it isn't null),
 * and finally, the size limit is applied if it is above 0.
 */
public class FSDocumentSelector implements DocumentSelector {

    //can be null!
    private final Pattern includeFileName;

    //can be null!
    private final Pattern excludeFileName;
    private final long maxFileSizeBytes;
    private final long minFileSizeBytes;

    public FSDocumentSelector(Pattern includeFileName, Pattern excludeFileName, long minFileSizeBytes,
                              long maxFileSizeBytes) {
        this.includeFileName = includeFileName;
        this.excludeFileName = excludeFileName;
        this.minFileSizeBytes = minFileSizeBytes;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public boolean select(Metadata metadata) {
        String fName = metadata.get(Metadata.RESOURCE_NAME_KEY);
        long sz = PropsUtil.getLong(metadata.get(Metadata.CONTENT_LENGTH), -1L);
        if (maxFileSizeBytes > -1 && sz > 0) {
            if (sz > maxFileSizeBytes) {
                return false;
            }
        }

        if (minFileSizeBytes > -1 && sz > 0) {
            if (sz < minFileSizeBytes) {
                return false;
            }
        }

        if (excludeFileName != null && fName != null) {
            Matcher m = excludeFileName.matcher(fName);
            if (m.find()) {
                return false;
            }
        }

        if (includeFileName != null && fName != null) {
            Matcher m = includeFileName.matcher(fName);
            return m.find();
        }
        return true;
    }

}
