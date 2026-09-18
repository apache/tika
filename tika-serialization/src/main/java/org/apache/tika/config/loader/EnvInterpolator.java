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
package org.apache.tika.config.loader;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.apache.tika.exception.TikaConfigException;

/**
 * Resolves {@code ${env:NAME}} inside the string values of a startup config to the
 * environment variable's value, in place. Only that exact form is touched: {@code ${NAME}},
 * {@code $NAME} and everything else stay literal. A reference to an unset variable fails the
 * load, naming the variable and the JSON path but never a value. Applied by
 * {@link TikaJsonConfig#load} only, never to JSON a request supplies.
 *
 * @since Apache Tika 4.1
 */
final class EnvInterpolator {

    static final Pattern REFERENCE = Pattern.compile("\\$\\{env:([A-Za-z_][A-Za-z0-9_]*)}");

    private EnvInterpolator() {
    }

    static void resolve(JsonNode root, Function<String, String> env) throws TikaConfigException {
        resolve(root, env, "");
    }

    private static void resolve(JsonNode node, Function<String, String> env, String path)
            throws TikaConfigException {
        if (node instanceof ObjectNode) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String childPath = path + "/" + field.getKey();
                if (field.getValue().isTextual()) {
                    field.setValue(resolveText(field.getValue().textValue(), env, childPath));
                } else {
                    resolve(field.getValue(), env, childPath);
                }
            }
        } else if (node instanceof ArrayNode) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                String childPath = path + "/" + i;
                if (array.get(i).isTextual()) {
                    array.set(i, resolveText(array.get(i).textValue(), env, childPath));
                } else {
                    resolve(array.get(i), env, childPath);
                }
            }
        }
    }

    private static TextNode resolveText(String text, Function<String, String> env, String path)
            throws TikaConfigException {
        Matcher m = REFERENCE.matcher(text);
        if (!m.find()) {
            return TextNode.valueOf(text);
        }
        StringBuilder sb = new StringBuilder();
        do {
            String name = m.group(1);
            String value = env.apply(name);
            if (value == null) {
                throw new TikaConfigException("environment variable " + name
                        + " is not set; referenced at " + path);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        } while (m.find());
        m.appendTail(sb);
        return TextNode.valueOf(sb.toString());
    }
}
