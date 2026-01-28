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
                ", throwOnZeroBytes=" + throwOnZeroBytes + '}';
    }
}
