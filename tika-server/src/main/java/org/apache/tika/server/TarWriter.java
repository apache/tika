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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;

@Provider
@Produces("application/x-tar")
public class TarWriter implements MessageBodyWriter<Map<String, byte[]>> {
  private static void tarStoreBuffer(TarArchiveOutputStream zip, String name, byte[] dataBuffer) throws IOException {
    TarArchiveEntry entry = new TarArchiveEntry(name);

    entry.setSize(dataBuffer.length);

    zip.putArchiveEntry(entry);

    zip.write(dataBuffer);

    zip.closeArchiveEntry();
  }

  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return Map.class.isAssignableFrom(type);
  }

  public long getSize(Map<String, byte[]> stringMap, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }

  public void writeTo(Map<String, byte[]> parts, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
    TarArchiveOutputStream zip = new TarArchiveOutputStream(entityStream);

    for (Map.Entry<String, byte[]> entry : parts.entrySet()) {
      tarStoreBuffer(zip, entry.getKey(), entry.getValue());
    }

    zip.close();
  }
}
