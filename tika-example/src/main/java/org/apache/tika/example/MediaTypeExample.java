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

package org.apache.tika.example;

import java.util.Map;
import java.util.Set;

import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

public class MediaTypeExample {
    public static void describeMediaType() {
        MediaType type = MediaType.parse("text/plain; charset=UTF-8");

        System.out.println("type:    " + type.getType());
        System.out.println("subtype: " + type.getSubtype());

        Map<String, String> parameters = type.getParameters();
        System.out.println("parameters:");
        for (Map.Entry<String,String> entry : parameters.entrySet()) {
            System.out.println("  " + entry.getKey() + "=" + entry.getValue());
        }
    }

    public static void listAllTypes() {
        MediaTypeRegistry registry = MediaTypeRegistry.getDefaultRegistry();

        for (MediaType type : registry.getTypes()) {
            Set<MediaType> aliases = registry.getAliases(type);
            System.out.println(type + ", also known as " + aliases);
        }
    }

    public static void main(String[] args) throws Exception {
        MediaTypeRegistry registry = MediaTypeRegistry.getDefaultRegistry();

        MediaType type = MediaType.parse("image/svg+xml");
        while (type != null) {
            System.out.println(type);
            type = registry.getSupertype(type);
        }
    }
}
