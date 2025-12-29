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
package org.apache.tika.pipes.ignite;

import java.io.Serializable;

import org.apache.tika.plugins.ExtensionConfig;

/**
 * Serializable wrapper for ExtensionConfig to work with Ignite's binary serialization.
 * Since ExtensionConfig is a Java record with final fields, it cannot be directly
 * serialized by Ignite. This DTO provides mutable fields that Ignite can work with.
 */
public class ExtensionConfigDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String json;

    public ExtensionConfigDTO() {
    }

    public ExtensionConfigDTO(String id, String name, String json) {
        this.id = id;
        this.name = name;
        this.json = json;
    }

    public ExtensionConfigDTO(ExtensionConfig config) {
        this.id = config.id();
        this.name = config.name();
        this.json = config.json();
    }

    public ExtensionConfig toExtensionConfig() {
        return new ExtensionConfig(id, name, json);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }
}
