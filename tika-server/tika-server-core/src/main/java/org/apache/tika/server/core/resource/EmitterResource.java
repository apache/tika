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

package org.apache.tika.server.core.resource;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.tika.emitter.Emitter;
import org.apache.tika.emitter.TikaEmitterException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.server.core.MetadataList;
import org.apache.tika.utils.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.tika.server.core.resource.TikaResource.fillMetadata;
import static org.apache.tika.server.core.resource.TikaResource.fillParseContext;

@Path("/emit")
public class EmitterResource {

    private static final String EMITTER_PARAM = "emitter";
    private static final Logger LOG = LoggerFactory.getLogger(EmitterResource.class);


    /**
     * Returns an InputStream that can be deserialized as a list of
     * {@link Metadata} objects.
     * The first in the list represents the main document, and the
     * rest represent metadata for the embedded objects.  This works
     * recursively through all descendants of the main document, not
     * just the immediate children.
     * <p>
     * The extracted text content is stored with the key
     * {@link org.apache.tika.sax.AbstractRecursiveParserWrapperHandler#TIKA_CONTENT}
     * <p>
     * Must specify an emitter in the path, e.g. /emit/solr
     * @param info uri info
     * @param emitterName which emitter to use; emitters must be configured in
     *                    the TikaConfig file.
     * @return InputStream that can be deserialized as a list of {@link Metadata} objects
     * @throws Exception
     */

    @PUT
    @Produces("application/json")
    @Path("{" + EMITTER_PARAM + " : (\\w+)?}")
    public Map<String, String> getMetadata(InputStream is,
                                @Context HttpHeaders httpHeaders,
                                @Context UriInfo info,
                                @PathParam(EMITTER_PARAM) String emitterName
                                ) throws Exception {

        String status = "ok";
        String exceptionMsg = "";
        Metadata metadata = new Metadata();
        List<Metadata> metadataList =
                RecursiveMetadataResource.parseMetadata(TikaResource.getInputStream(is, metadata,
                        httpHeaders),
						metadata,
						httpHeaders.getRequestHeaders(), info, "text");

        Emitter emitter = TikaResource.getConfig().getEmitter();
        try {
            emitter.emit(emitterName, metadataList);
        } catch (IOException|TikaEmitterException e) {
            LOG.warn("problem with emitting", e);
            status = "emitter_exception";
            exceptionMsg = ExceptionUtils.getStackTrace(e);
        }
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", status);
        statusMap.put("emitter", emitterName);
        if (exceptionMsg.length() > 0) {
            statusMap.put("exception_msg", exceptionMsg);
        }
        return statusMap;
    }

}
