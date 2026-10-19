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

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Represents a resource entry in a Frictionless Data Package.
 * See: https://specs.frictionlessdata.io/data-resource/
 *
 * @param name         Frictionless resource name, derived from {@code path}: required,
 *                     unique, lowercase {@code [a-z0-9._-]}
 * @param path         The relative path to the file within the package (e.g., "unpacked/1.pdf")
 * @param mediatype    The MIME type of the file (e.g., "application/pdf")
 * @param bytes        The file size in bytes
 * @param hash         The SHA256 hash in format "sha256:hexstring"
 * @param embeddedId   the file's {@code tk:embedded-id}, 0 for the container; matches its
 *                     row in rmeta.json, if present
 * @param resourceName {@code tk:resource-name}, the original filename, if there was one
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"name", "path", "mediatype", "bytes", "hash", "tk:embedded-id", "tk:resource-name"})
public record FrictionlessResource(
        @JsonProperty("name") String name,
        @JsonProperty("path") String path,
        @JsonProperty("mediatype") String mediatype,
        @JsonProperty("bytes") long bytes,
        @JsonProperty("hash") String hash,
        @JsonProperty("tk:embedded-id") Integer embeddedId,
        @JsonProperty("tk:resource-name") String resourceName
) {

    /** A package file that is not an embedded document, e.g. rmeta.json. */
    public static FrictionlessResource create(String path, String mediatype, long bytes,
                                              String hash) {
        return new FrictionlessResource(nameFromPath(path), path, mediatype, bytes, hash,
                null, null);
    }

    /** An embedded document under unpacked/. */
    public static FrictionlessResource create(String path, String mediatype, long bytes,
                                              String hash, int embeddedId, String resourceName) {
        return new FrictionlessResource(nameFromPath(path), path, mediatype, bytes, hash,
                embeddedId, resourceName);
    }

    /** Paths are unique within a package, so the names derived from them are too. */
    static String nameFromPath(String path) {
        return path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
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
