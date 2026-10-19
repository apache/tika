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

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Represents a Frictionless Data Package manifest (datapackage.json).
 * See: https://specs.frictionlessdata.io/data-package/
 *
 * The Data Package format is a simple standard for packaging data with metadata.
 * This implementation includes:
 * - name: identifier for the package (typically the container filename)
 * - created: ISO 8601 timestamp of when the package was created
 * - resources: list of data files with path, mediatype, bytes, and hash
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"name", "created", "title", "description", "resources"})
public class DataPackage {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ROOT)
                    .withZone(ZoneOffset.UTC);

    @JsonProperty("name")
    private String name;

    @JsonProperty("created")
    private String created;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("resources")
    private List<FrictionlessResource> resources;

    /**
     * Creates a new DataPackage with the given name and current timestamp.
     *
     * @param name the package name (typically container filename)
     */
    public DataPackage(String name) {
        this.name = sanitizeName(name);
        this.created = ISO_FORMATTER.format(Instant.now());
        this.resources = new ArrayList<>();
    }

    /**
     * Creates an empty DataPackage for deserialization.
     */
    public DataPackage() {
        this.resources = new ArrayList<>();
    }

    /**
     * Sanitizes the name to be a valid Frictionless package name.
     * Package names should be lowercase with no spaces.
     *
     * @param name the raw name
     * @return sanitized name
     */
    private static String sanitizeName(String name) {
        if (name == null) {
            return "unknown";
        }
        // Replace spaces, keep alphanumeric, dots, hyphens, underscores
        return name.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-")
                .replaceAll("-+", "-");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = sanitizeName(name);
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<FrictionlessResource> getResources() {
        return resources;
    }

    public void setResources(List<FrictionlessResource> resources) {
        this.resources = resources;
    }

    /**
     * Adds a resource to this data package.
     *
     * @param resource the resource to add
     */
    public void addResource(FrictionlessResource resource) {
        this.resources.add(resource);
    }

    /**
     * Adds a resource to this data package with all parameters.
     *
     * @param path      relative path within package
     * @param mediatype MIME type
     * @param bytes     file size
     * @param hash      SHA256 hash with "sha256:" prefix
     * @param name      optional original filename
     */
    public void addResource(String path, String mediatype, long bytes, String hash, String name) {
        this.resources.add(FrictionlessResource.create(path, mediatype, bytes, hash, name));
    }

    /**
     * Serializes this DataPackage to JSON.
     *
     * @return JSON string representation
     * @throws IOException if serialization fails
     */
    public String toJson() throws IOException {
        return MAPPER.writeValueAsString(this);
    }

    /**
     * Writes this DataPackage as JSON to the given output stream.
     * Does not close the output stream.
     *
     * @param outputStream the stream to write to
     * @throws IOException if serialization fails
     */
    public void writeTo(OutputStream outputStream) throws IOException {
        MAPPER.writeValue(outputStream, this);
    }

    /**
     * Parses a DataPackage from JSON string.
     *
     * @param json the JSON string
     * @return parsed DataPackage
     * @throws IOException if parsing fails
     */
    public static DataPackage fromJson(String json) throws IOException {
        return MAPPER.readValue(json, DataPackage.class);
    }

    /**
     * Returns true if this package has any resources.
     *
     * @return true if resources list is not empty
     */
    public boolean hasResources() {
        return resources != null && !resources.isEmpty();
    }

    /**
     * Returns the number of resources in this package.
     *
     * @return resource count
     */
    public int resourceCount() {
        return resources != null ? resources.size() : 0;
    }

    @Override
    public String toString() {
        return "DataPackage{" +
                "name='" + name + '\'' +
                ", created='" + created + '\'' +
                ", resources=" + (resources != null ? resources.size() : 0) + " items" +
                '}';
    }
}
