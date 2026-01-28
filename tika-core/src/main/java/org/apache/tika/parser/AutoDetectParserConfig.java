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
package org.apache.tika.parser;

import java.io.Serializable;

import org.xml.sax.ContentHandler;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.digest.Digester;
import org.apache.tika.digest.DigesterFactory;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.ContentHandlerDecoratorFactory;

/**
 * Configuration for AutoDetectParser behavior.
 * <p>
 * Note: Security limits (zip bomb thresholds, XML depth, etc.) are now configured
 * via {@link org.apache.tika.config.OutputLimits} in the ParseContext, not here.
 * <p>
 * This is a config POJO. It uses standard Jackson deserialization for its
 * primitive fields, but component fields (like embeddedDocumentExtractorFactory)
 * use compact format.
 */
@TikaComponent(spi = false)
public class AutoDetectParserConfig implements Serializable {

    private static ContentHandlerDecoratorFactory NOOP_CONTENT_HANDLER_DECORATOR_FACTORY =
            new ContentHandlerDecoratorFactory() {
                @Override
                public ContentHandler decorate(ContentHandler contentHandler, Metadata metadata,
                                               ParseContext parseContext) {
                    return contentHandler;
                }
            };

    public static AutoDetectParserConfig DEFAULT = new AutoDetectParserConfig();

    private EmbeddedDocumentExtractorFactory embeddedDocumentExtractorFactory = null;

    private ContentHandlerDecoratorFactory contentHandlerDecoratorFactory =
            NOOP_CONTENT_HANDLER_DECORATOR_FACTORY;

    private DigesterFactory digesterFactory = null;

    // Lazily built digester from the factory
    private transient Digester digester = null;

    /**
     * If true, skip digesting for container (top-level) documents.
     * Only embedded documents will be digested.
     */
    private boolean skipContainerDocumentDigest = false;

    private boolean throwOnZeroBytes = true;

    public AutoDetectParserConfig() {
    }

    public void setEmbeddedDocumentExtractorFactory(
            EmbeddedDocumentExtractorFactory embeddedDocumentExtractorFactory) {
        this.embeddedDocumentExtractorFactory = embeddedDocumentExtractorFactory;
    }

    public EmbeddedDocumentExtractorFactory getEmbeddedDocumentExtractorFactory() {
        return embeddedDocumentExtractorFactory;
    }

    public void setContentHandlerDecoratorFactory(
            ContentHandlerDecoratorFactory contentHandlerDecoratorFactory) {
        this.contentHandlerDecoratorFactory = contentHandlerDecoratorFactory;
    }

    public ContentHandlerDecoratorFactory getContentHandlerDecoratorFactory() {
        return contentHandlerDecoratorFactory;
    }

    /**
     * Sets the digester factory.
     * This is the preferred method for configuring digesting via JSON serialization.
     *
     * @param digesterFactory the digester factory
     */
    public void setDigesterFactory(DigesterFactory digesterFactory) {
        this.digesterFactory = digesterFactory;
    }

    /**
     * Gets the digester factory.
     *
     * @return the digester factory, or null if not configured
     */
    public DigesterFactory getDigesterFactory() {
        return digesterFactory;
    }

    /**
     * Returns the Digester, lazily building it from the factory if needed.
     * <p>
     * Note: This method is intentionally not named getDigester() to avoid
     * Jackson treating it as a bean property during serialization.
     *
     * @return the Digester, or null if no factory is configured
     */
    public Digester digester() {
        if (digester == null && digesterFactory != null) {
            digester = digesterFactory.build();
        }
        return digester;
    }

    /**
     * Sets the digester directly. This is useful for programmatic configuration
     * (e.g., from command-line arguments) when you don't have a DigesterFactory.
     * <p>
     * Note: This method is intentionally not named setDigester() to avoid
     * Jackson treating it as a bean property during deserialization.
     *
     * @param digester the digester to use
     */
    public void digester(Digester digester) {
        this.digester = digester;
    }

    /**
     * Returns whether to skip digesting for container (top-level) documents.
     *
     * @return true if container documents should be skipped, false otherwise
     */
    public boolean isSkipContainerDocumentDigest() {
        return skipContainerDocumentDigest;
    }

    /**
     * Sets whether to skip digesting for container (top-level) documents.
     *
     * @param skipContainerDocumentDigest if true, only embedded documents will be digested
     */
    public void setSkipContainerDocumentDigest(boolean skipContainerDocumentDigest) {
        this.skipContainerDocumentDigest = skipContainerDocumentDigest;
    }

    public void setThrowOnZeroBytes(boolean throwOnZeroBytes) {
        this.throwOnZeroBytes = throwOnZeroBytes;
    }

    public boolean getThrowOnZeroBytes() {
        return throwOnZeroBytes;
    }

    @Override
    public String toString() {
        return "AutoDetectParserConfig{" +
                "embeddedDocumentExtractorFactory=" + embeddedDocumentExtractorFactory +
                ", contentHandlerDecoratorFactory=" + contentHandlerDecoratorFactory +
                ", digesterFactory=" + digesterFactory +
                ", skipContainerDocumentDigest=" + skipContainerDocumentDigest +
                ", throwOnZeroBytes=" + throwOnZeroBytes + '}';
    }
}
