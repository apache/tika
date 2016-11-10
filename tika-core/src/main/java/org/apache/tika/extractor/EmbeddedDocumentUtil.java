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
package org.apache.tika.extractor;


import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.utils.ExceptionUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Utility class to handle common issues with embedded documents.
 * <p/>
 * Use statically if all that is needed is getting the EmbeddedDocumentExtractor.
 * Otherwise, instantiate an instance.
 * <p/>
 * Note: This is not thread safe.  Make sure to instantiate one per thread.
 */
public class EmbeddedDocumentUtil implements Serializable {

    private final ParseContext context;
    private final EmbeddedDocumentExtractor embeddedDocumentExtractor;
    //these are lazily initialized and can be null
    private TikaConfig tikaConfig;
    private MimeTypes mimeTypes;
    private Detector detector;

    public EmbeddedDocumentUtil(ParseContext context) {
        this.context = context;
        this.embeddedDocumentExtractor = getEmbeddedDocumentExtractor(context);
    }

    public static EmbeddedDocumentExtractor getEmbeddedDocumentExtractor(ParseContext context) {
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class);
        if (extractor == null) {
            extractor = new ParsingEmbeddedDocumentExtractor(context);
        }
        return extractor;
    }


    public PasswordProvider getPasswordProvider() {
        return context.get(PasswordProvider.class);
    }

    public Detector getDetector() {
        //be as lazy as possible and cache the detector
        if (detector == null) {
            detector = context.get(Detector.class);
            if (detector == null) {
                detector = getTikaConfig().getDetector();
            }
        }
        return detector;
    }

    public MimeTypes getMimeTypes() {
        //be as lazy as possible and cache the mimeTypes
        if (mimeTypes == null) {
            mimeTypes = context.get(MimeTypes.class);
            if (mimeTypes == null) {
                mimeTypes = getTikaConfig().getMimeRepository();
            }
        }
        return mimeTypes;
    }

    public TikaConfig getTikaConfig() {
        //be as lazy as possible and cache the TikaConfig
        if (tikaConfig == null) {
            tikaConfig = context.get(TikaConfig.class);
            if (tikaConfig == null) {
                tikaConfig = TikaConfig.getDefaultConfig();
            }
        }
        return tikaConfig;
    }

    public String getExtension(TikaInputStream is, Metadata metadata) {
        String mimeString = metadata.get(Metadata.CONTENT_TYPE);
        TikaConfig config = getConfig();
        MimeType mimeType = null;
        MimeTypes types = config.getMimeRepository();
        boolean detected = false;
        if (mimeString != null) {
            try {
                 mimeType = types.forName(mimeString);
            } catch (MimeTypeException e) {
                //swallow
            }
        }
        if (mimeType == null) {
            Detector detector = config.getDetector();
            try {
                MediaType mediaType = detector.detect(is, metadata);
                mimeType = types.forName(mediaType.toString());
                detected = true;
                is.reset();
            } catch (IOException e) {
                //swallow
            } catch (MimeTypeException e) {
                //swallow
            }
        }
        if (mimeType != null) {
            if (detected) {
                //set or correct the mime type
                metadata.set(Metadata.CONTENT_TYPE, mimeType.toString());
            }
            return mimeType.getExtension();
        }
        return ".bin";
    }

    public TikaConfig getConfig() {
        TikaConfig config = context.get(TikaConfig.class);
        if (config == null) {
            config = TikaConfig.getDefaultConfig();
        }
        return config;
    }

    public static void recordException(Throwable t, Metadata m) {
        String ex = ExceptionUtils.getFilteredStackTrace(t);
        m.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, ex);
    }

    public boolean shouldParseEmbedded(Metadata m) {
        return getEmbeddedDocumentExtractor().shouldParseEmbedded(m);
    }

    private EmbeddedDocumentExtractor getEmbeddedDocumentExtractor() {
        return embeddedDocumentExtractor;
    }

    public void parseEmbedded(InputStream inputStream, ContentHandler handler,
                              Metadata metadata, boolean outputHtml) throws IOException, SAXException {
        embeddedDocumentExtractor.parseEmbedded(inputStream, handler, metadata, outputHtml);
    }
}
