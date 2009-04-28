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
package org.apache.tika.detect;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Content type detection based on a content type hint. This detector simply
 * trusts any valid content type hint given in the input metadata, and returns
 * that as the likely type of the input document.
 *
 * @since Apache Tika 0.3
 */
public class TypeDetector implements Detector {

    /**
     * Detects the content type of an input document based on a type hint
     * given in the input metadata. The CONTENT_TYPE attribute of the given
     * input metadata is expected to contain the type of the input document.
     * If that attribute exists and contains a valid type name, then that
     * type is returned.
     *
     * @param input ignored
     * @param metadata input metadata, possibly with a CONTENT_TYPE value
     * @return detected media type, or <code>application/octet-stream</code>
     */
    public MediaType detect(InputStream input, Metadata metadata) {
        // Look for a type hint in the input metadata
        String hint = metadata.get(Metadata.CONTENT_TYPE);
        if (hint != null) {
            MediaType type = MediaType.parse(hint);
            if (type != null) {
                return type;
            }
        }
        return MediaType.OCTET_STREAM;
    }

}
