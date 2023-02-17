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

import java.io.IOException;
import java.io.Serializable;

import org.w3c.dom.Element;
import org.xml.sax.ContentHandler;

import org.apache.tika.config.ConfigBase;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractorFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.writefilter.MetadataWriteFilterFactory;
import org.apache.tika.sax.ContentHandlerDecoratorFactory;

/**
 * This config object can be used to tune how conservative we want to be
 * when parsing data that is extremely compressible and resembles a ZIP
 * bomb. Null values will be ignored and will not affect the default values
 * in SecureContentHandler.
 * <p>
 *     See <a href="https://cwiki.apache.org/confluence/display/TIKA/ModifyingContentWithHandlersAndMetadataFilters"/>ModifyingContentWithHandlersAndMetadataFilters</a>
 *     for documentation and examples for configuring this with a tika-config.xml file.
 */
public class AutoDetectParserConfig extends ConfigBase implements Serializable {

    private static ContentHandlerDecoratorFactory NOOP_CONTENT_HANDLER_DECORATOR_FACTORY =
            new ContentHandlerDecoratorFactory() {
                @Override
                public ContentHandler decorate(ContentHandler contentHandler, Metadata metadata) {
                    return contentHandler;
                }

                @Override
                public ContentHandler decorate(ContentHandler contentHandler, Metadata metadata,
                                               ParseContext parseContext) {
                    return contentHandler;
                }
            };

    public static AutoDetectParserConfig DEFAULT = new AutoDetectParserConfig();

    public static AutoDetectParserConfig load(Element element)
            throws TikaConfigException, IOException {
        return AutoDetectParserConfig.buildSingle("autoDetectParserConfig",
                AutoDetectParserConfig.class, element, AutoDetectParserConfig.DEFAULT);
    }

    /**
     * If this is not null and greater than -1, the AutoDetectParser
     * will spool the stream to disk if the length of the stream is known
     * ahead of time.
     */
    private Long spoolToDisk = null;

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

    private MetadataWriteFilterFactory metadataWriteFilterFactory = null;

    private EmbeddedDocumentExtractorFactory embeddedDocumentExtractorFactory =
            new ParsingEmbeddedDocumentExtractorFactory();

    private ContentHandlerDecoratorFactory contentHandlerDecoratorFactory =
            NOOP_CONTENT_HANDLER_DECORATOR_FACTORY;

    private DigestingParser.DigesterFactory digesterFactory = null;

    private boolean throwOnZeroBytes = true;

    /**
     * Creates a SecureContentHandlerConfig using the passed in parameters.
     *
     * @param spoolToDisk
     * @param outputThreshold          SecureContentHandler - character output threshold.
     * @param maximumCompressionRatio  SecureContentHandler - max compression ratio allowed.
     * @param maximumDepth             SecureContentHandler - maximum XML element nesting level.
     * @param maximumPackageEntryDepth SecureContentHandler - maximum package entry nesting level.
     */
    public AutoDetectParserConfig(Long spoolToDisk, Long outputThreshold,
                                  Long maximumCompressionRatio, Integer maximumDepth,
                                  Integer maximumPackageEntryDepth) {
        this.spoolToDisk = spoolToDisk;
        this.outputThreshold = outputThreshold;
        this.maximumCompressionRatio = maximumCompressionRatio;
        this.maximumDepth = maximumDepth;
        this.maximumPackageEntryDepth = maximumPackageEntryDepth;
    }

    public AutoDetectParserConfig() {

    }

    public Long getSpoolToDisk() {
        return spoolToDisk;
    }

    public void setSpoolToDisk(long spoolToDisk) {
        this.spoolToDisk = spoolToDisk;
    }

    public Long getOutputThreshold() {
        return outputThreshold;
    }

    public void setOutputThreshold(long outputThreshold) {
        this.outputThreshold = outputThreshold;
    }

    public Long getMaximumCompressionRatio() {
        return maximumCompressionRatio;
    }

    public void setMaximumCompressionRatio(long maximumCompressionRatio) {
        this.maximumCompressionRatio = maximumCompressionRatio;
    }

    public Integer getMaximumDepth() {
        return maximumDepth;
    }

    public void setMaximumDepth(int maximumDepth) {
        this.maximumDepth = maximumDepth;
    }

    public Integer getMaximumPackageEntryDepth() {
        return maximumPackageEntryDepth;
    }

    public void setMaximumPackageEntryDepth(int maximumPackageEntryDepth) {
        this.maximumPackageEntryDepth = maximumPackageEntryDepth;
    }

    public MetadataWriteFilterFactory getMetadataWriteFilterFactory() {
        return this.metadataWriteFilterFactory;
    }

    public void setMetadataWriteFilterFactory(
            MetadataWriteFilterFactory metadataWriteFilterFactory) {
        this.metadataWriteFilterFactory = metadataWriteFilterFactory;
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

    public void setDigesterFactory(DigestingParser.DigesterFactory digesterFactory) {
        this.digesterFactory = digesterFactory;
    }

    public DigestingParser.DigesterFactory getDigesterFactory() {
        return this.digesterFactory;
    }

    public void setThrowOnZeroBytes(boolean throwOnZeroBytes) {
        this.throwOnZeroBytes = throwOnZeroBytes;
    }

    public boolean getThrowOnZeroBytes() {
        return throwOnZeroBytes;
    }

    @Override
    public String toString() {
        return "AutoDetectParserConfig{" + "spoolToDisk=" + spoolToDisk + ", outputThreshold=" +
                outputThreshold + ", maximumCompressionRatio=" + maximumCompressionRatio +
                ", maximumDepth=" + maximumDepth + ", maximumPackageEntryDepth=" +
                maximumPackageEntryDepth + ", metadataWriteFilterFactory=" +
                metadataWriteFilterFactory + ", embeddedDocumentExtractorFactory=" +
                embeddedDocumentExtractorFactory + ", contentHandlerDecoratorFactory=" +
                contentHandlerDecoratorFactory + ", digesterFactory=" + digesterFactory +
                ", throwOnZeroBytes=" + throwOnZeroBytes + '}';
    }
}

