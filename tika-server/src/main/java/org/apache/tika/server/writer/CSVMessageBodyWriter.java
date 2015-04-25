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
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;

@Provider
@Produces("text/csv")
public class CSVMessageBodyWriter implements MessageBodyWriter<Metadata> {

    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return Metadata.class.isAssignableFrom(type);
    }

    public long getSize(Metadata data, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    @SuppressWarnings("resource")
    public void writeTo(Metadata metadata, Class<?> type, Type genericType, Annotation[] annotations,
                        MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException,
            WebApplicationException {

        CSVWriter writer = new CSVWriter(new OutputStreamWriter(entityStream, IOUtils.UTF_8));

        for (String name : metadata.names()) {
            String[] values = metadata.getValues(name);
            ArrayList<String> list = new ArrayList<String>(values.length + 1);
            list.add(name);
            list.addAll(Arrays.asList(values));
            writer.writeNext(list.toArray(values));
        }

        // Don't close, just flush the stream
        writer.flush();
    }
}
