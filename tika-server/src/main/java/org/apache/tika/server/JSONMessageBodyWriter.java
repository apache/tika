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

package org.apache.tika.server;

import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.eclipse.jetty.util.ajax.JSON;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JSONMessageBodyWriter implements MessageBodyWriter<Metadata> {

  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return Metadata.class.isAssignableFrom(type);
  }

  public long getSize(Metadata data, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }

  @Override
  public void writeTo(Metadata metadata, Class<?> type, Type genericType, Annotation[] annotations,
      MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException,
      WebApplicationException {

      Map<String, Object> res = new TreeMap<String, Object>();

    for (String name : metadata.names()) {
      String[] values = metadata.getValues(name);
      if (metadata.isMultiValued(name)) {
        res.put(name, values);
      } else {
        res.put(name, values[0]);
      }
    }

    String json = JSON.toString(res);
    System.err.println("JSON : "+json);
    StringReader r = new StringReader(json);
    IOUtils.copy(r, entityStream);
    entityStream.flush();
  }
}
