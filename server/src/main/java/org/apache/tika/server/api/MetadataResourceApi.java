package org.apache.tika.server.api;

import java.util.Map;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;
import org.apache.cxf.jaxrs.ext.multipart.*;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ApiResponse;
import io.swagger.jaxrs.PATCH;
import javax.validation.constraints.*;
import javax.validation.Valid;

/**
 * Tika JAX-RS Server
 *
 * <p>The Tika server implements [JAX-RS](http://en.wikipedia.org/wiki/JAX-RS) (Java API for RESTful Web Services) to provide web services according to the Representational State Transfer (REST) architectural style. This facilitates a wide varity oif operations and flexibility with regards to both client and server implementations. The officially supported Tika server implementation is packaged using the OpenAPI [jaxrs-cxf generator](https://openapi-generator.tech/docs/generators/jaxrs-cxf]. This work was tracked through [TIKA-3082](https://issues.apache.org/jira/browse/TIKA-3082). <b>N.B.</b> the OpenAPI version always tracks the underlying Tika version to remove uncertainty about which version of Tika is running within the server.
 *
 */
@Path("/")
@Api(value = "/", description = "")
public interface MetadataResourceApi  {

    /**
     * PUT a document to the metadata extraction resource and get a specific metadata key&#39;s value.
     *
     * PUT a document to the metadata extraction resource and get a specific metadata key&#39;s value.
     *
     */
    @PUT
    @Path("/meta/{metadata_key}")
    @Produces({ "application/json" })
    @ApiOperation(value = "PUT a document to the metadata extraction resource and get a specific metadata key's value.", tags={ "Metadata Resource",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "If successful, this operation returns HTTP status code 200, with the body containing the extracted metadata value for the given key.", response = Map.class, responseContainer = "Map"),
        @ApiResponse(code = 500, message = "An error occurred processing the call.") })
    public Map<String, String> putDocumentGetMetaValue(@PathParam("metadata_key") String metadataKey);

    /**
     * PUT a document to the metadata extraction resource.
     *
     * PUT a document to the metadata extraction resource.
     *
     */
    @PUT
    @Path("/meta")
    @Produces({ "application/json", "text/plain" })
    @ApiOperation(value = "PUT a document to the metadata extraction resource.", tags={ "Metadata Resource" })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "If successful, this operation returns HTTP status code 200, with the body containing all of the extracted metadata for the uploaded data.", response = Map.class, responseContainer = "Map"),
        @ApiResponse(code = 500, message = "An error occurred processing the call.") })
    public Map<String, String> putDocumentMeta();
}

