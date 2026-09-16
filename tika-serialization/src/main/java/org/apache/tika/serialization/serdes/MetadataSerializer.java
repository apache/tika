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
package org.apache.tika.serialization.serdes;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class MetadataSerializer extends JsonSerializer<Metadata> {
    private static final String TIKA_CONTENT_KEY = TikaCoreProperties.TIKA_CONTENT.getName();

    //always sort the content at the end
    private static final Comparator<String> METADATA_KEY_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            if (o1.equals(TIKA_CONTENT_KEY)) {
                return 1;
            }
            if (o2.equals(TIKA_CONTENT_KEY)) {
                return -1;
            }
            return o1.compareTo(o2);
        }
    };

    private boolean prettyPrint = false;

    public MetadataSerializer() {

    }

    public MetadataSerializer(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }
    @Override
    public void serialize(Metadata metadata, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();
        String[] names = metadata.names();
        if (prettyPrint) {
            Arrays.sort(names, METADATA_KEY_COMPARATOR);
        }
        for (String n : names) {
            String[] v = metadata.getValues(n);
            if (v.length == 0) {
                continue;
            } else if (v.length == 1) {
                jsonGenerator.writeStringField(n, wellFormed(v[0]));
            } else {
                jsonGenerator.writeFieldName(n);
                jsonGenerator.writeArray(wellFormed(v), 0, v.length);
            }
        }
        jsonGenerator.writeEndObject();
    }

    private static String[] wellFormed(String[] values) {
        String[] out = null;
        for (int i = 0; i < values.length; i++) {
            String w = wellFormed(values[i]);
            if (w != values[i]) {
                if (out == null) {
                    out = values.clone();
                }
                out[i] = w;
            }
        }
        return out == null ? values : out;
    }

    /**
     * The value with every unpaired surrogate replaced by U+FFFD. A parser can hand back a lone
     * surrogate (an HTML numeric character reference for one, say); a UTF-8 or Smile generator
     * rejects it, and one bad value must not fail the whole document.
     */
    static String wellFormed(String value) {
        if (value == null) {
            return null;
        }
        int n = value.length();
        StringBuilder out = null;
        for (int i = 0; i < n; i++) {
            char c = value.charAt(i);
            if (!Character.isSurrogate(c)) {
                if (out != null) {
                    out.append(c);
                }
                continue;
            }
            boolean paired = Character.isHighSurrogate(c) && i + 1 < n
                    && Character.isLowSurrogate(value.charAt(i + 1));
            if (paired) {
                if (out != null) {
                    out.append(c).append(value.charAt(i + 1));
                }
                i++;
                continue;
            }
            if (out == null) {
                out = new StringBuilder(n);
                out.append(value, 0, i);
            }
            out.append('\uFFFD');
        }
        return out == null ? value : out.toString();
    }
}
