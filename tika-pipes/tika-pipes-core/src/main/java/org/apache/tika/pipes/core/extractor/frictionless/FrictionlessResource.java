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
package org.apache.tika.pipes.core.extractor.frictionless;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a resource entry in a Frictionless Data Package.
 * See: https://specs.frictionlessdata.io/data-resource/
 *
 * @param path      The relative path to the file within the package (e.g., "unpacked/00000001.pdf")
 * @param mediatype The MIME type of the file (e.g., "application/pdf")
 * @param bytes     The file size in bytes
 * @param hash      The SHA256 hash in format "sha256:hexstring"
 * @param name      Optional original filename if available
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FrictionlessResource(
        @JsonProperty("path") String path,
        @JsonProperty("mediatype") String mediatype,
        @JsonProperty("bytes") long bytes,
        @JsonProperty("hash") String hash,
        @JsonProperty("name") String name
) {

    /**
     * Creates a FrictionlessResource with all fields.
     *
     * @param path      relative path within package
     * @param mediatype MIME type
     * @param bytes     file size
     * @param hash      SHA256 hash with "sha256:" prefix
     * @param name      optional original filename
     * @return new FrictionlessResource
     */
    public static FrictionlessResource create(String path, String mediatype, long bytes,
                                              String hash, String name) {
        return new FrictionlessResource(path, mediatype, bytes, hash, name);
    }

    /**
     * Creates a FrictionlessResource without the optional name field.
     *
     * @param path      relative path within package
     * @param mediatype MIME type
     * @param bytes     file size
     * @param hash      SHA256 hash with "sha256:" prefix
     * @return new FrictionlessResource
     */
    public static FrictionlessResource create(String path, String mediatype, long bytes,
                                              String hash) {
        return new FrictionlessResource(path, mediatype, bytes, hash, null);
    }

    /**
     * Formats a SHA256 byte array as the Frictionless hash string format.
     *
     * @param sha256Bytes the raw SHA256 hash bytes
     * @return hash string in format "sha256:hexstring"
     */
    public static String formatHash(byte[] sha256Bytes) {
        StringBuilder sb = new StringBuilder("sha256:");
        for (byte b : sha256Bytes) {
            sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
