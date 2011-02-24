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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipOutputStream;

public class ZipOutput implements StreamingOutput {
  private final Map<PartExtractor, Collection> parts = new HashMap<PartExtractor, Collection>();

  public <T> void put(PartExtractor<T> extractor, Collection<T> parts) {
    if (parts.isEmpty()) {
      return;
    }

    this.parts.put(extractor, parts);
  }

  public void write(OutputStream outputStream) throws IOException, WebApplicationException {
    ZipOutputStream zip = new ZipOutputStream(outputStream);

    zip.setMethod(ZipOutputStream.STORED);

    addParts(zip);

    zip.close();
  }

  private void addParts(ZipOutputStream zip) throws IOException {
    for (Map.Entry<PartExtractor, Collection> entry : parts.entrySet()) {
      for (Object part : entry.getValue()) {
        entry.getKey().extract(part, zip);
      }
    }
  }

  public boolean isEmpty() {
    return parts.isEmpty();
  }
}
