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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.InputStream;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.server.MetadataList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/rmeta")
public class RecursiveMetadataResource {

    private static final String HANDLER_TYPE_PARAM = "handler";
    private static final BasicContentHandlerFactory.HANDLER_TYPE DEFAULT_HANDLER_TYPE =
            BasicContentHandlerFactory.HANDLER_TYPE.XML;
    private static final Logger LOG = LoggerFactory.getLogger(RecursiveMetadataResource.class);

    /**
     * Returns an InputStream that can be deserialized as a list of
     * {@link Metadata} objects.
     * The first in the list represents the main document, and the
     * rest represent metadata for the embedded objects.  This works
     * recursively through all descendants of the main document, not
     * just the immediate children.
     * <p>
     * The extracted text content is stored with the key
     * {@link RecursiveParserWrapper#TIKA_CONTENT}.
     * <p>
     * Specify the handler for the content (xml, html, text, ignore)
     * in the path:<br/>
     * /rmeta/form (default: xml)<br/>
     * /rmeta/form/xml    (store the content as xml)<br/>
     * /rmeta/form/text   (store the content as text)<br/>
     * /rmeta/form/ignore (don't record any content)<br/>
     *
     * @param att attachment
     * @param info uri info
     * @param handlerTypeName which type of handler to use
     * @return InputStream that can be deserialized as a list of {@link Metadata} objects
     * @throws Exception
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces({"application/json"})
    @Path("form{" + HANDLER_TYPE_PARAM + " : (\\w+)?}")
    public Response getMetadataFromMultipart(Attachment att, @Context UriInfo info,
                                             @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName)
            throws Exception {
        return Response.ok(
                parseMetadata(att.getObject(InputStream.class), att.getHeaders(), info, handlerTypeName)).build();
    }

    /**
     * Returns an InputStream that can be deserialized as a list of
     * {@link Metadata} objects.
     * The first in the list represents the main document, and the
     * rest represent metadata for the embedded objects.  This works
     * recursively through all descendants of the main document, not
     * just the immediate children.
     * <p>
     * The extracted text content is stored with the key
     * {@link RecursiveParserWrapper#TIKA_CONTENT}.
     * <p>
     * Specify the handler for the content (xml, html, text, ignore)
     * in the path:<br/>
     * /rmeta (default: xml)<br/>
     * /rmeta/xml    (store the content as xml)<br/>
     * /rmeta/text   (store the content as text)<br/>
     * /rmeta/ignore (don't record any content)<br/>
     *
     * @param info uri info
     * @param handlerTypeName which type of handler to use
     * @return InputStream that can be deserialized as a list of {@link Metadata} objects
     * @throws Exception
     */

    @PUT
    @Produces("application/json")
    @Path("{" + HANDLER_TYPE_PARAM + " : (\\w+)?}")
    public Response getMetadata(InputStream is,
                                @Context HttpHeaders httpHeaders,
                                @Context UriInfo info,
                                @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName
                                ) throws Exception {
        return Response.ok(
                parseMetadata(TikaResource.getInputStream(is, httpHeaders),
						httpHeaders.getRequestHeaders(), info, handlerTypeName)).build();
    }

	private MetadataList parseMetadata(InputStream is,
			MultivaluedMap<String, String> httpHeaders, UriInfo info, String handlerTypeName)
			throws Exception {
		final Metadata metadata = new Metadata();
		final ParseContext context = new ParseContext();
		Parser parser = TikaResource.createParser();
		// TODO: parameterize choice of max chars/max embedded attachments
		BasicContentHandlerFactory.HANDLER_TYPE type =
                BasicContentHandlerFactory.parseHandlerType(handlerTypeName, DEFAULT_HANDLER_TYPE);
		RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser,
				new BasicContentHandlerFactory(type, -1));
		TikaResource.fillMetadata(parser, metadata, context, httpHeaders);
		// no need to add parser to parse recursively
		TikaResource.fillParseContext(context, httpHeaders, null);
		TikaResource.logRequest(LOG, info, metadata);
		TikaResource.parse(wrapper, LOG, info.getPath(), is,
				new LanguageHandler() {
					public void endDocument() {
						metadata.set("language", getLanguage().getLanguage());
					}
				}, metadata, context);
		return new MetadataList(wrapper.getMetadata());
	}
}
