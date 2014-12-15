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

import java.io.InputStream;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This JAX-RS endpoint provides access to the metadata contained within a
 * document. It is possible to submit a relatively small prefix (a few KB) of a
 * document's content to retrieve individual metadata fields.
 * <p>
 */
@Path("/metadata")
public class MetadataEP {
  private static final Log logger = LogFactory.getLog(MetadataEP.class);
  
  private TikaConfig config;
  private final AutoDetectParser parser;

  /** The metdata for the request */
  private final Metadata metadata = new Metadata();
  private final ParseContext context = new ParseContext();

  public MetadataEP(@Context HttpHeaders httpHeaders, @Context UriInfo info) {
    // TODO How to get this better?
    config = TikaConfig.getDefaultConfig();
    parser = TikaResource.createParser(config);
    
    TikaResource.fillMetadata(parser, metadata, context, httpHeaders.getRequestHeaders());
    TikaResource.logRequest(logger, info, metadata);
  }

  /**
   * Get all metadata that can be parsed from the specified input stream. An
   * error is produced if the input stream cannot be parsed.
   * 
   * @param is
   *          an input stream
   * @return the metadata
   * @throws Exception
   */
  @POST
  public Response getMetadata(InputStream is) throws Exception {
    parser.parse(is, new DefaultHandler(), metadata, context);
    return Response.ok(metadata).build();
  }

  /**
   * Get a specific TIKA metadata field as a simple text string. If the field is
   * multivalued, then only the first value is returned. If the input stream
   * cannot be parsed, but a value was found for the given metadata field, then
   * the value of the field is returned as part of a 200 OK response; otherwise
   * a {@link Status#BAD_REQUEST} is generated. If the stream was successfully
   * parsed but the specific metadata field was not found, then a
   * {@link Status#NOT_FOUND} is returned.
   * <p>
   * 
   * @param field
   *          the tika metadata field name
   * @param is
   *          the document stream
   * @return one of {@link Status#OK}, {@link Status#NOT_FOUND}, or
   *         {@link Status#BAD_REQUEST}
   * @throws Exception
   */
  @POST
  @Path("{field}")
  @Produces(MediaType.TEXT_PLAIN)
  public Response getSimpleMetadataField(@PathParam("field") String field, InputStream is) throws Exception {

    // use BAD request to indicate that we may not have had enough data to
    // process the request
    Status defaultErrorResponse = Status.BAD_REQUEST;
    try {
      parser.parse(is, new DefaultHandler(), metadata, context);
      // once we've parsed the document successfully, we should use NOT_FOUND
      // if we did not see the field
      defaultErrorResponse = Status.NOT_FOUND;
    } catch (Exception e) {
      logger.info("Failed to process field " + field, e);
    }
    String value = metadata.get(field);
    if (value == null) {
      return Response.status(defaultErrorResponse).entity("Failed to get metadata field " + field).build();
    }
    return Response.ok(value, MediaType.TEXT_PLAIN_TYPE).build();
  }

  /**
   * Get a specific metadata field. If the input stream cannot be parsed, but a
   * value was found for the given metadata field, then the value of the field
   * is returned as part of a 200 OK response; otherwise a
   * {@link Status#BAD_REQUEST} is generated. If the stream was successfully
   * parsed but the specific metadata field was not found, then a
   * {@link Status#NOT_FOUND} is returned.
   * <p>
   * Note that this method handles multivalue fields and returns possibly more
   * metadata than requested.
   * 
   * @param field
   *          the tika metadata field name
   * @param is
   *          the document stream
   * @return one of {@link Status#OK}, {@link Status#NOT_FOUND}, or
   *         {@link Status#BAD_REQUEST}
   * @throws Exception
   */
  @POST
  @Path("{field}")
  public Response getMetadataField(@PathParam("field") String field, InputStream is) throws Exception {

    // use BAD request to indicate that we may not have had enough data to
    // process the request
    Status defaultErrorResponse = Status.BAD_REQUEST;
    try {
      parser.parse(is, new DefaultHandler(), metadata, context);
      // once we've parsed the document successfully, we should use NOT_FOUND
      // if we did not see the field
      defaultErrorResponse = Status.NOT_FOUND;
    } catch (Exception e) {
      logger.info("Failed to process field " + field, e);
    }
    String[] values = metadata.getValues(field);
    if (values.length == 0) {
      return Response.status(defaultErrorResponse).entity("Failed to get metadata field " + field).build();
    }
    // remove fields we don't care about for the response
    for (String name : metadata.names()) {
      if (!field.equals(name)) {
        metadata.remove(name);
      }
    }
    return Response.ok(metadata).build();
  }

}
