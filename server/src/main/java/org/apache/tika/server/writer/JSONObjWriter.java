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

package org.apache.tika.server.writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.tika.metadata.Metadata;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JSONObjWriter implements MessageBodyWriter<Map<String, Object>> {
    private static Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return Map.class.isAssignableFrom(type);
    }

    public long getSize(Metadata data, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(Map<String, Object> map, Class<?> type, Type genericType, Annotation[] annotations,
                        MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException {
        Writer writer = new OutputStreamWriter(entityStream, UTF_8);
        GSON.toJson(map, writer);
        writer.flush();
        entityStream.flush();
    }

    /**
     * TODO this method override needs to be populated correctly.
     */
    @Override
    public long getSize(Map<String, Object> t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
      // TODO Auto-generated method stub
      return 0;
    }
}
