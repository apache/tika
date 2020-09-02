package org.apache.tika.server.api.impl;

import org.apache.tika.server.api.*;

/**
 * Tika JAX-RS Server
 *
 * <p>The Tika server implements [JAX-RS](http://en.wikipedia.org/wiki/JAX-RS) (Java API for RESTful Web Services) to provide web services according to the Representational State Transfer (REST) architectural style. This facilitates a wide varity oif operations and flexibility with regards to both client and server implementations. The officially supported Tika server implementation is packaged using the OpenAPI [jaxrs-cxf generator](https://openapi-generator.tech/docs/generators/jaxrs-cxf]. This work was tracked through [TIKA-3082](https://issues.apache.org/jira/browse/TIKA-3082). <b>N.B.</b> the OpenAPI version always tracks the underlying Tika version to remove uncertainty about which version of Tika is running within the server.
 *
 */
public class LanguageResourceApiServiceImpl implements LanguageResourceApi {
    /**
     * POST a UTF-8 text file to the LanguageIdentifier to identify its language.
     *
     * POST a UTF-8 text file to the LanguageIdentifier to identify its language. &lt;b&gt;NOTE&lt;/b&gt;: This endpoint does not parse files.  It runs detection on a UTF-8 string.
     *
     */
    public String postLanguageStream() {
        // TODO: Implement...
        
        return null;
    }
    
    /**
     * POST a text string to the LanguageIdentifier to identify its language.
     *
     * POST a text string to the LanguageIdentifier to identify its language.
     *
     */
    public String postLanguageString() {
        // TODO: Implement...
        
        return null;
    }
    
    /**
     * PUT a UTF-8 text file to the LanguageIdentifier to identify its language.
     *
     * POST a UTF-8 text file to the LanguageIdentifier to identify its language. &lt;b&gt;NOTE&lt;/b&gt;: This endpoint does not parse files.  It runs detection on a UTF-8 string.
     *
     */
    public String putLanguageStream() {
        // TODO: Implement...
        
        return null;
    }
    
    /**
     * PUT a text string to the LanguageIdentifier to identify its language.
     *
     * PUT a text string to the LanguageIdentifier to identify its language.
     *
     */
    public String putLanguageString() {
        // TODO: Implement...
        
        return null;
    }
    
}

