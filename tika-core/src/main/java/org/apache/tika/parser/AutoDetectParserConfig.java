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
 * This config object can be used to tune how conservative we want to be
 * when parsing data that is extremely compressible and resembles a ZIP
 * bomb. Null values will be ignored and will not affect the default values
 * in SecureContentHandler.
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

    /**
     * SecureContentHandler -- Desired output threshold in characters.
     */
    private Long outputThreshold = null;

    /**
     * SecureContentHandler -- Desired maximum compression ratio.
     */
    private Long maximumCompressionRatio = null;

    /**
     * SecureContentHandler -- Desired maximum XML nesting level.
     */
    private Integer maximumDepth = null;

    /**
     * SecureContentHandler -- Desired maximum package entry nesting level.
     */
    private Integer maximumPackageEntryDepth = null;

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

    /**
     * Creates a SecureContentHandlerConfig using the passed in parameters.
     *
     * @param outputThreshold          SecureContentHandler - character output threshold.
     * @param maximumCompressionRatio  SecureContentHandler - max compression ratio allowed.
     * @param maximumDepth             SecureContentHandler - maximum XML element nesting level.
     * @param maximumPackageEntryDepth SecureContentHandler - maximum package entry nesting level.
     */
    public AutoDetectParserConfig(Long outputThreshold,
                                  Long maximumCompressionRatio, Integer maximumDepth,
                                  Integer maximumPackageEntryDepth) {
        this.outputThreshold = outputThreshold;
        this.maximumCompressionRatio = maximumCompressionRatio;
        this.maximumDepth = maximumDepth;
        this.maximumPackageEntryDepth = maximumPackageEntryDepth;
    }

    public AutoDetectParserConfig() {

    }

    public Long getOutputThreshold() {
        return outputThreshold;
    }

    public void setOutputThreshold(Long outputThreshold) {
        this.outputThreshold = outputThreshold;
    }

    public Long getMaximumCompressionRatio() {
        return maximumCompressionRatio;
    }

    public void setMaximumCompressionRatio(Long maximumCompressionRatio) {
        this.maximumCompressionRatio = maximumCompressionRatio;
    }

    public Integer getMaximumDepth() {
        return maximumDepth;
    }

    public void setMaximumDepth(Integer maximumDepth) {
        this.maximumDepth = maximumDepth;
    }

    public Integer getMaximumPackageEntryDepth() {
        return maximumPackageEntryDepth;
    }

    public void setMaximumPackageEntryDepth(Integer maximumPackageEntryDepth) {
        this.maximumPackageEntryDepth = maximumPackageEntryDepth;
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
        return "AutoDetectParserConfig{" + "outputThreshold=" +
                outputThreshold + ", maximumCompressionRatio=" + maximumCompressionRatio +
                ", maximumDepth=" + maximumDepth + ", maximumPackageEntryDepth=" +
                maximumPackageEntryDepth + ", embeddedDocumentExtractorFactory=" +
                embeddedDocumentExtractorFactory + ", contentHandlerDecoratorFactory=" +
                contentHandlerDecoratorFactory + ", digesterFactory=" + digesterFactory +
                ", skipContainerDocumentDigest=" + skipContainerDocumentDigest +
                ", throwOnZeroBytes=" + throwOnZeroBytes + '}';
    }
}
