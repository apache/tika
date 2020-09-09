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
import org.apache.tika.server.model.DefaultDetector;
import org.apache.tika.server.model.DetailedParsers;
import org.apache.tika.server.model.Parsers;

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
public interface InformationServicesApi  {

    /**
     * GET information about the top level detector to be used, and any child detectors within it.
     *
     * The top level detector to be used, and any child detectors within it. Available as plain text, json or human readable HTML
     *
     */
    @GET
    @Path("/detectors")
    @Produces({ "application/json" })
    @ApiOperation(value = "GET information about the top level detector to be used, and any child detectors within it.", tags={ "Information Services",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "If successful, this operation returns HTTP status code 200, with the body the default detector information.", response = DefaultDetector.class),
        @ApiResponse(code = 500, message = "An error occurred processing the call.") })
    public DefaultDetector getDetectors();

    /**
     * GET a list of all server endpoints
     *
     * Hitting the route of the server will give a basic report of all the endpoints defined in the server, what URL they have etc.
     *
     */
    @GET
    @Path("/")
    @Produces({ "text/html", "text/plain" })
    @ApiOperation(value = "GET a list of all server endpoints", tags={ "Information Services",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "If successful, this operation returns HTTP status code 200, with the body containing a list of endpoints.", response = String.class),
        @ApiResponse(code = 500, message = "An error occurred processing the call.") })
    public String getEndpoints();

    /**
     * GET all mime types, their aliases, their supertype, and the parser.
     *
     * Mime types, their aliases, their supertype, and the parser. Available as plain text, json or human readable HTML.
     *
     */
    @GET
    @Path("/mime-types")
    @Produces({ "application/json" })
    @ApiOperation(value = "GET all mime types, their aliases, their supertype, and the parser.", tags={ "Information Services",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "If successful, this operation returns HTTP status code 200, with the body containing Mime types, their aliases, their supertype, and the parser.", response = Map.class, responseContainer = "Map"),
        @ApiResponse(code = 500, message = "An error occurred processing the call.") })
    public Map<String, Object> getMimetypes();

    /**
     * GET all of the parsers currently available.
     *
     * Lists all of the parsers currently available.
     *
     */
    @GET
    @Path("/parsers")
    @Produces({ "application/json" })
    @ApiOperation(value = "GET all of the parsers currently available.", tags={ "Information Services",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "If successful, this operation returns HTTP status code 200, with the body containing a list of parser objects.", response = Parsers.class),
        @ApiResponse(code = 500, message = "An error occurred processing the call.") })
    public Parsers getParsers();

    /**
     * GET all the available parsers, along with what mimetypes they support.
     *
     * List all the available parsers, along with what mimetypes they support.
     *
     */
    @GET
    @Path("/parsers/details")
    @Produces({ "application/json" })
    @ApiOperation(value = "GET all the available parsers, along with what mimetypes they support.", tags={ "Information Services" })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "If successful, this operation returns HTTP status code 200, with the body containing a list of parser object details including the mime types handled by each parser.", response = DetailedParsers.class),
        @ApiResponse(code = 500, message = "An error occurred processing the call.") })
    public DetailedParsers getParsersDetails();
}

