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

package org.apache.tika.parser.html.tagsoup;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;

import org.apache.tika.mime.MediaType;

public class DataURIScheme {


    private final String rawMediaTypeString;
    private final boolean isBase64;
    private final byte[] data;

    DataURIScheme(String mediaTypeString, boolean isBase64, byte[] data) {
        this.rawMediaTypeString = mediaTypeString;
        this.isBase64 = isBase64;
        this.data = data;
    }

    public InputStream getInputStream() {
        return new UnsynchronizedByteArrayInputStream(data);
    }

    /**
     * @return parsed media type or <code>null</code> if parse fails or if media type string was
     * not specified
     */
    public MediaType getMediaType() {
        if (rawMediaTypeString != null) {
            return MediaType.parse(rawMediaTypeString);
        }
        return null;
    }

    public boolean isBase64() {
        return isBase64;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataURIScheme)) {
            return false;
        }
        DataURIScheme that = (DataURIScheme) o;
        return isBase64() == that.isBase64() &&
                Objects.equals(rawMediaTypeString, that.rawMediaTypeString) &&
                Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {

        int result = Objects.hash(rawMediaTypeString, isBase64());
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }
}
