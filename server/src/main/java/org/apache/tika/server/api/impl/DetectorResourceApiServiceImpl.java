package org.apache.tika.server.api.impl;

import org.apache.tika.server.api.*;

/**
 * Tika JAX-RS Server
 *
 * <p>The Tika server implements [JAX-RS](http://en.wikipedia.org/wiki/JAX-RS) (Java API for RESTful Web Services) to provide web services according to the Representational State Transfer (REST) architectural style. This facilitates a wide varity oif operations and flexibility with regards to both client and server implementations. The officially supported Tika server implementation is packaged using the OpenAPI [jaxrs-cxf generator](https://openapi-generator.tech/docs/generators/jaxrs-cxf]. This work was tracked through [TIKA-3082](https://issues.apache.org/jira/browse/TIKA-3082). <b>N.B.</b> the OpenAPI version always tracks the underlying Tika version to remove uncertainty about which version of Tika is running within the server.
 *
 */
public class DetectorResourceApiServiceImpl implements DetectorResourceApi {
    /**
     * PUT a document and use the default detector to identify the MIME/media type.
     *
     * PUT a document and use the default detector to identify the MIME/media type. The caveat here is that providing a hint for the filename can increase the quality of detection. Default return is a string of the Media type name.
     *
     */
    public String putStream() {
        // TODO: Implement...
        
        return null;
    }
    
}

