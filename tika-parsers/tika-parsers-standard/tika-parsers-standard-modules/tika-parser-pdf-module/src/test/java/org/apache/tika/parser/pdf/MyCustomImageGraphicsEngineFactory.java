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
package org.apache.tika.parser.pdf;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDPage;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.image.ImageGraphicsEngine;
import org.apache.tika.parser.pdf.image.ImageGraphicsEngineFactory;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Example custom ImageGraphicsEngineFactory demonstrating how users can create
 * their own factory implementations with custom configuration parameters.
 * <p>
 * <b>JSON Config File Usage:</b> Use the class name string approach:
 * <pre>
 * {
 *   "pdf-parser": {
 *     "imageGraphicsEngineFactoryClass": "com.example.MyCustomFactory"
 *   }
 * }
 * </pre>
 * Note: This approach does not support custom parameters; the factory will use default values.
 * <p>
 * <b>ParseContext Serialization:</b> The {@code @JsonTypeInfo} annotation enables polymorphic
 * serialization when using tika-serialization's polymorphic ObjectMapper (e.g., for
 * ParseContext round-trip serialization). This requires the annotation on both the base
 * class and subclass for full polymorphic support.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public class MyCustomImageGraphicsEngineFactory extends ImageGraphicsEngineFactory {

    /**
     * Metadata key used to record that this custom factory was used during parsing.
     */
    public static final String CUSTOM_FACTORY_USED = "X-CustomGraphicsEngineFactory-Used";

    /**
     * Metadata key used to record the customParam value.
     */
    public static final String CUSTOM_PARAM_KEY = "X-CustomGraphicsEngineFactory-CustomParam";

    private String customParam = "default";

    public MyCustomImageGraphicsEngineFactory() {
        // Default constructor required for Jackson deserialization
    }

    public String getCustomParam() {
        return customParam;
    }

    public void setCustomParam(String customParam) {
        this.customParam = customParam;
    }

    @Override
    public ImageGraphicsEngine newEngine(PDPage page,
                                         int pageNumber,
                                         EmbeddedDocumentExtractor embeddedDocumentExtractor,
                                         PDFParserConfig pdfParserConfig,
                                         Map<COSStream, Integer> processedInlineImages,
                                         AtomicInteger imageCounter, XHTMLContentHandler xhtml,
                                         Metadata parentMetadata, ParseContext parseContext) {
        // Record that this custom factory was used
        parentMetadata.set(CUSTOM_FACTORY_USED, "true");
        parentMetadata.set(CUSTOM_PARAM_KEY, customParam);

        // Delegate to the default implementation
        return super.newEngine(page, pageNumber, embeddedDocumentExtractor, pdfParserConfig,
                processedInlineImages, imageCounter, xhtml, parentMetadata, parseContext);
    }
}
