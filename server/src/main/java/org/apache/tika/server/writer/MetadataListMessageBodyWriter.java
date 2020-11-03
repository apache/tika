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

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.server.MetadataList;

import static java.nio.charset.StandardCharsets.UTF_8;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class MetadataListMessageBodyWriter implements MessageBodyWriter<MetadataList> {

    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        if (!MediaType.APPLICATION_JSON_TYPE.equals(mediaType)) {
            return false;
        }
        return type.isAssignableFrom(MetadataList.class);
    }

    public long getSize(MetadataList data, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(MetadataList list, Class<?> type, Type genericType, Annotation[] annotations,
                        MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException,
            WebApplicationException {
        try {
            Writer writer = new OutputStreamWriter(entityStream, UTF_8);
            JsonMetadataList.toJson(list.getMetadata(), writer);
            writer.flush();
        } catch (TikaException e) {
            throw new IOException(e);
        }
        entityStream.flush();
    }
}
