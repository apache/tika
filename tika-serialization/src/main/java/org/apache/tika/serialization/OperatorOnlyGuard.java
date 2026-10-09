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
package org.apache.tika.serialization;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

import org.apache.tika.config.OperatorOnly;

/**
 * Refuses per-request JSON that names an {@link OperatorOnly} property. Runs on the JSON
 * tree before deserialization, so no setter sees the value. Properties are matched the way
 * Jackson will bind them (declared name and aliases); a key Jackson does not know is left
 * for Jackson to refuse. Object values are checked against the nested property's type.
 */
public final class OperatorOnlyGuard {

    private record Property(boolean operatorOnly, JavaType type) { }

    private static final Map<JavaType, Map<String, Property>> PROPERTIES = new ConcurrentHashMap<>();

    private OperatorOnlyGuard() {
    }

    /**
     * @throws IOException if {@code json} names an operator-only property of {@code configClass}
     */
    public static void check(ObjectMapper mapper, String configKey, String json, Class<?> configClass)
            throws IOException {
        check(mapper, configKey, mapper.readTree(json), mapper.constructType(configClass));
    }

    static void check(ObjectMapper mapper, String configKey, JsonNode node, JavaType type)
            throws IOException {
        if (node == null || !node.isObject()) {
            return;
        }
        Map<String, Property> properties = PROPERTIES.computeIfAbsent(type, t -> introspect(mapper, t));
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            Property property = properties.get(e.getKey());
            if (property == null) {
                continue;
            }
            if (property.operatorOnly()) {
                throw new IOException("Cannot modify " + e.getKey() + " at runtime: per-request config for '"
                        + configKey + "' may not set it. Configure it at initialization time "
                        + "(tika-config.json or a preset).");
            }
            if (e.getValue().isObject() && !property.type().isContainerType()) {
                check(mapper, configKey, e.getValue(), property.type());
            }
        }
    }

    private static Map<String, Property> introspect(ObjectMapper mapper, JavaType type) {
        BeanDescription bean = mapper.getDeserializationConfig().introspect(type);
        Map<String, Property> properties = new HashMap<>();
        for (BeanPropertyDefinition def : bean.findProperties()) {
            Property property = new Property(isOperatorOnly(def), def.getPrimaryType());
            properties.put(def.getName(), property);
            for (PropertyName alias : def.findAliases()) {
                properties.put(alias.getSimpleName(), property);
            }
        }
        return properties;
    }

    private static boolean isOperatorOnly(BeanPropertyDefinition def) {
        for (AnnotatedMember member : new AnnotatedMember[]{def.getSetter(), def.getGetter(), def.getField()}) {
            if (member != null && isAnnotated(member.getMember())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnnotated(Member member) {
        if (member instanceof Field field) {
            return field.isAnnotationPresent(OperatorOnly.class);
        }
        if (member instanceof Method method) {
            // an override that drops the annotation must not unlock the property
            for (Class<?> c = method.getDeclaringClass(); c != null; c = c.getSuperclass()) {
                try {
                    if (c.getDeclaredMethod(method.getName(), method.getParameterTypes())
                            .isAnnotationPresent(OperatorOnly.class)) {
                        return true;
                    }
                } catch (NoSuchMethodException ignored) {
                    // not declared at this level
                }
            }
        }
        return false;
    }
}
