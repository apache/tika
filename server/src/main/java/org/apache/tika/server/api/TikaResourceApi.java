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
package org.apache.tika.server.api;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ApiResponse;

/**
 * Tika JAX-RS Server
 *
 * <p>The Tika server implements [JAX-RS](http://en.wikipedia.org/wiki/JAX-RS) (Java API for RESTful Web Services) to provide web services according to the Representational State Transfer (REST) architectural style. This facilitates a wide varity oif operations and flexibility with regards to both client and server implementations. The officially supported Tika server implementation is packaged using the OpenAPI [jaxrs-cxf generator](https://openapi-generator.tech/docs/generators/jaxrs-cxf]. This work was tracked through [TIKA-3082](https://issues.apache.org/jira/browse/TIKA-3082). <b>N.B.</b> the OpenAPI version always tracks the underlying Tika version to remove uncertainty about which version of Tika is running within the server.
 *
 */
@Path("/")
@Api(value = "/", description = "")
public interface TikaResourceApi  {

    /**
     * GET returns a greeting stating the server is up.
     *
     * HTTP GET returns a greeting stating the server is up. Followed by a PUT request to extract text.
     *
     */
    @GET
    @Path("/tika")
    @Produces({ "text/plain" })
    @ApiOperation(value = "GET returns a greeting stating the server is up.", tags={ "Tika Resource",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "If successful, this operation returns HTTP status code 200, with a greeting to indicate the server is up and you may PUT a file.", response = String.class),
        @ApiResponse(code = 500, message = "An error occurred processing the call.") })
    public String getTika();

    /**
     * GET returns a greeting stating the server is up.
     *
     * HTTP PUTs a document to the /tika service and you get back the extracted text.
     *
     */
    @PUT
    @Path("/tika")
    @Produces({ "text/plain" })
    @ApiOperation(value = "GET returns a greeting stating the server is up.", tags={ "Tika Resource" })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "If successful, this operation returns HTTP status code 200, with the extraacted text.", response = String.class),
        @ApiResponse(code = 500, message = "An error occurred processing the call.") })
    public String putTika();
}

