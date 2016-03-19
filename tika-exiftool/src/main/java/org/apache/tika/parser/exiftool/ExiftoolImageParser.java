/*
 * Copyright (C) 2005-2013 Alfresco Software Limited.
 *
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
package org.apache.tika.parser.exiftool;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.IPTC;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.image.ImageMetadataExtractor;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.image.xmp.JempboxExtractor;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A parser which uses the {@link ExiftoolIptcMetadataExtractor} to perform the work of metadata extraction.
 *
 * @author rgauss
 *
 */
public class ExiftoolImageParser extends AbstractParser {

    private static final long serialVersionUID = 1469157231567542637L;

    private String exiftoolExecutable;
    private Collection<Property> passthroughXmpProperties;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                    MediaType.image("jpeg"),
                    MediaType.image("png"),
                    MediaType.image("tiff"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        // TODO Check for availability of command line before returning?
        return SUPPORTED_TYPES;
    }

    public ExiftoolImageParser() {
        super();
    }

    public ExiftoolImageParser(String exiftoolExecutable) {
        super();
        this.exiftoolExecutable = exiftoolExecutable;
    }
    
    public ExiftoolImageParser(String exiftoolExecutable, Collection<Property> passthroughXmpProperties) {
        super();
        this.exiftoolExecutable = exiftoolExecutable;
        this.passthroughXmpProperties = passthroughXmpProperties;
    }

    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            MediaType mediaType = MediaType.parse(metadata.get(Metadata.CONTENT_TYPE));
            if (mediaType != null && mediaType.equals(MediaType.image("jpeg"))) {
                new ImageMetadataExtractor(metadata).parseJpeg(tis.getFile());
            }
            if(mediaType != null && mediaType.equals(MediaType.image("tiff"))) {
            	new ImageMetadataExtractor(metadata).parseTiff(tis.getFile());
            }
            tis.mark(Integer.MAX_VALUE);
            ImageParser imageParser = new ImageParser();
            if (imageParser.getSupportedTypes(context).contains(mediaType)) {
                imageParser.parse(tis, handler, metadata, context);
                tis.reset();
            }
            
            // JempboxExtractor joins creators with comma, we want to preserve as is
            String iptcCreator = metadata.get(IPTC.CREATOR);
            
            new JempboxExtractor(metadata).parse(tis);
            tis.reset();
            
            metadata.set(IPTC.CREATOR, iptcCreator);
            
            new ExiftoolIptcMetadataExtractor(
                    metadata, getSupportedTypes(context), exiftoolExecutable, passthroughXmpProperties).parse(tis);
        } finally {
            tmp.dispose();
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
        
        cleanDuplicateKeywords(metadata);
    }
    
    /**
     * The {@link JempboxExtractor} calls 
     * <code>metadata.add(TikaCoreProperties.KEYWORDS, keywords.next());</code>
     * for each keyword and <code>metadata.add</code> does not check for duplicates.
     * The exiftool XML parsing may add them again, so we must forcibly remove duplicates
     * using this method.
     * 
     * @param metadata
     */
    protected void cleanDuplicateKeywords(Metadata metadata) {
        String[] keywords = metadata.getValues(TikaCoreProperties.KEYWORDS);
        if (keywords.length == 0) {
            return;
        }
        LinkedHashSet<String> cleanedKeywords = new LinkedHashSet<String>();
        for (int i = 0; i < keywords.length; i++)
        {
            cleanedKeywords.add(keywords[i]);
        }
        metadata.set(TikaCoreProperties.KEYWORDS, cleanedKeywords.toArray(new String[] {}));
    }

}
