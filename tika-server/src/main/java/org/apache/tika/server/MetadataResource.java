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

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.xml.sax.helpers.DefaultHandler;

import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;

@Path("/meta{id:(/.*)?}")
public class MetadataResource {
  private static final Log logger = LogFactory.getLog(MetadataResource.class);

  @PUT
  @Produces("text/csv")
  public StreamingOutput getMetadata(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
    final Metadata metadata = new Metadata();
    AutoDetectParser parser = TikaResource.createParser();
    TikaResource.fillMetadata(parser, metadata, httpHeaders);
    TikaResource.logRequest(logger, info, metadata);

    parser.parse(is, new DefaultHandler(), metadata);

    return new StreamingOutput() {
      public void write(OutputStream outputStream) throws IOException, WebApplicationException {
        metadataToCsv(metadata, outputStream);
      }
    };
  }

  public static void metadataToCsv(Metadata metadata, OutputStream outputStream) throws IOException {
    CSVWriter writer = new CSVWriter(new OutputStreamWriter(outputStream, "UTF-8"));

    for (String name : metadata.names()) {
      String[] values = metadata.getValues(name);
      ArrayList<String> list = new ArrayList<String>(values.length+1);
      list.add(name);
      list.addAll(Arrays.asList(values));
      writer.writeNext(list.toArray(values));
    }

    writer.close();
  }
}
