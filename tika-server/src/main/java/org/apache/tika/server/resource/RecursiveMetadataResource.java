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

package org.apache.tika.server.resource;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.tika.language.ProfilingHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.server.MetadataList;

@Path("/rmeta")
public class RecursiveMetadataResource {
    private static final Log logger = LogFactory.getLog(RecursiveMetadataResource.class);

    @POST
    @Consumes("multipart/form-data")
    @Produces({"text/csv", "application/json"})
    @Path("form")
    public Response getMetadataFromMultipart(Attachment att, @Context UriInfo info) throws Exception {
        return Response.ok(
                parseMetadata(att.getObject(InputStream.class), att.getHeaders(), info)).build();
    }

    @PUT
    @Produces("application/json")
    public Response getMetadata(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
		is = TikaUtils.getInputSteam(is, httpHeaders);
        return Response.ok(
                parseMetadata(is, httpHeaders.getRequestHeaders(), info)).build();
    }

	private MetadataList parseMetadata(InputStream is,
			MultivaluedMap<String, String> httpHeaders, UriInfo info)
			throws Exception {
		final Metadata metadata = new Metadata();
		final ParseContext context = new ParseContext();
		Parser parser = TikaResource.createParser();
		// TODO: parameterize choice of handler and max chars?
		BasicContentHandlerFactory.HANDLER_TYPE type = BasicContentHandlerFactory.HANDLER_TYPE.TEXT;
		RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser,
				new BasicContentHandlerFactory(type, -1));
		TikaResource.fillMetadata(parser, metadata, context, httpHeaders);
		// no need to add parser to parse recursively
		TikaResource.fillParseContext(context, httpHeaders, null);
		TikaResource.logRequest(logger, info, metadata);
		TikaResource.parse(wrapper, logger, info.getPath(), is,
				new ProfilingHandler() {
					public void endDocument() {
						metadata.set("language", getLanguage().getLanguage());
					}
				}, metadata, context);
		return new MetadataList(wrapper.getMetadata());
	}
}
