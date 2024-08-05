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

import static org.apache.tika.serialization.ParseContextSerializer.PARSE_CONTEXT;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.io.IOExceptionWithCause;

import org.apache.tika.parser.ParseContext;

public class ParseContextDeserializer extends JsonDeserializer<ParseContext> {

    @Override
    public ParseContext deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        JsonNode root = jsonParser.getCodec().readTree(jsonParser);
        return readParseContext(root);
    }

    public static ParseContext readParseContext(JsonNode jsonNode) throws IOException {
        //some use cases include the wrapper node, e.g. { "parseContext": {}}
        //some include the contents only.
        //Try to find "parseContext" to start. If that doesn't exist, assume the jsonNode is the contents.
        JsonNode contextNode = jsonNode.get(PARSE_CONTEXT);

        if (contextNode == null) {
            contextNode = jsonNode;
        }
        ParseContext parseContext = new ParseContext();
        Iterator<Map.Entry<String, JsonNode>> it = contextNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String superClassName = e.getKey();
            JsonNode obj = e.getValue();
            String className = readVal(TikaJsonSerializer.INSTANTIATED_CLASS_KEY, obj, null, true);
            try {
                Class clazz = Class.forName(className);
                Class superClazz = className.equals(superClassName) ? clazz : Class.forName(superClassName);
                parseContext.set(superClazz, TikaJsonDeserializer.deserialize(clazz, superClazz, obj));
            } catch (ReflectiveOperationException ex) {
                throw new IOExceptionWithCause(ex);
            }
        }
        return parseContext;
    }

    private static String readVal(String key, JsonNode jsonObj, String defaultRet, boolean isRequired) throws IOException {
        JsonNode valNode = jsonObj.get(key);
        if (valNode == null) {
            if (isRequired) {
                throw new IOException("required value string, but see: " + key);
            }
            return defaultRet;
        }
        return valNode.asText();
    }
}
