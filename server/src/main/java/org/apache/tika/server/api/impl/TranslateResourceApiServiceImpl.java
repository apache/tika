package org.apache.tika.server.api.impl;

import org.apache.tika.server.api.*;

/**
 * Tika JAX-RS Server
 *
 * <p>The Tika server implements [JAX-RS](http://en.wikipedia.org/wiki/JAX-RS) (Java API for RESTful Web Services) to provide web services according to the Representational State Transfer (REST) architectural style. This facilitates a wide varity oif operations and flexibility with regards to both client and server implementations. The officially supported Tika server implementation is packaged using the OpenAPI [jaxrs-cxf generator](https://openapi-generator.tech/docs/generators/jaxrs-cxf]. This work was tracked through [TIKA-3082](https://issues.apache.org/jira/browse/TIKA-3082). <b>N.B.</b> the OpenAPI version always tracks the underlying Tika version to remove uncertainty about which version of Tika is running within the server.
 *
 */
public class TranslateResourceApiServiceImpl implements TranslateResourceApi {
    /**
     * POST a document and auto-detects the *src* language and translates to *dest*
     *
     * POST a document and translates from the *src* language to the *dest*. &lt;b&gt;NOTE&lt;/b&gt;:  *translator* should be a fully qualified Tika class name (with package) and *dest* should be the 2 character short code for the source language.
     *
     */
    public String postTranslateAllSrcDest() {
        // TODO: Implement...
        
        return null;
    }
    
    /**
     * POST a document and translates from the *src* language to the *dest*
     *
     * POST a document and translates from the *src* language to the *dest*. &lt;b&gt;NOTE&lt;/b&gt;:  *translator* should be a fully qualified Tika class name (with package), *src* and *dest* should be the 2 character short code for the source language and dest language respectively.
     *
     */
    public String postTranslateAllTranslatorSrcDest() {
        // TODO: Implement...
        
        return null;
    }
    
    /**
     * PUT a document and auto-detects the *src* language and translates to *dest*
     *
     * PUT a document and translates from the *src* language to the *dest*. &lt;b&gt;NOTE&lt;/b&gt;:  *translator* should be a fully qualified Tika class name (with package) and *dest* should be the 2 character short code for the source language.
     *
     */
    public String putTranslateAllSrcDest() {
        // TODO: Implement...
        
        return null;
    }
    
    /**
     * PUT a document and translates from the *src* language to the *dest*
     *
     * PUT a document and translates from the *src* language to the *dest*. &lt;b&gt;NOTE&lt;/b&gt;:  *translator* should be a fully qualified Tika class name (with package), *src* and *dest* should be the 2 character short code for the source language and dest language respectively.
     *
     */
    public String putTranslateAllTranslatorSrcDest() {
        // TODO: Implement...
        
        return null;
    }
    
}

