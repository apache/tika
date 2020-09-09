package org.apache.tika.server.api;


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
public interface RecursiveMetadataAndContentApi  {

    /**
     * Returns a JSONified list of Metadata objects for the container document and all embedded documents.
     *
     * Returns an InputStream that can be deserialized as a list of {@link Metadata} objects. The first in the list represents the main document, and the rest represent metadata for the embedded objects. This works recursively through all descendants of the main document, not just the immediate children. The extracted text content is stored with the key {@link RecursiveParserWrapper#TIKA_CONTENT}.
     *
     */
    @PUT
    @Path("/rmeta")
    @Produces({ "text/plain" })
    @ApiOperation(value = "Returns a JSONified list of Metadata objects for the container document and all embedded documents.", tags={ "Recursive Metadata and Content" })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Returns a JSONified list of Metadata objects for the container document and all embedded documents.", response = String.class),
        @ApiResponse(code = 500, message = "An error occurred processing the call.") })
    public String putRmeta();
}

