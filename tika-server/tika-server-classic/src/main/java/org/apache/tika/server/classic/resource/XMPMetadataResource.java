package org.apache.tika.server.classic.resource;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.server.core.resource.MetadataResource;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.resource.TikaServerResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.InputStream;

public class XMPMetadataResource extends MetadataResource implements TikaServerResource {

    @PUT
    @Path("{field}")
    @Produces({"application/rdf+xml"})
    @Override
    public Response getMetadataField(InputStream is, @Context HttpHeaders httpHeaders,
                                     @Context UriInfo info, @PathParam("field") String field) throws Exception {
        return super.getMetadataField(is, httpHeaders, info, field);
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces({"application/rdf+xml"})
    @Path("form")
    public Response getMetadataFromMultipart(Attachment att, @Context UriInfo info) throws Exception {
        return Response.ok(
                parseMetadata(att.getObject(InputStream.class), new Metadata(),
                        att.getHeaders(), info)).build();
    }

    @PUT
    @Produces({"application/rdf+xml"})
    public Response getMetadata(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
        Metadata metadata = new Metadata();
        return Response.ok(
                parseMetadata(TikaResource.getInputStream(is, metadata, httpHeaders), metadata, httpHeaders.getRequestHeaders(), info)).build();
    }
}
