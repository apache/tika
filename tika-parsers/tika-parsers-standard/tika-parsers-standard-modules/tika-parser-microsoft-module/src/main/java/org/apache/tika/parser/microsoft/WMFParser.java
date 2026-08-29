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
package org.apache.tika.parser.microsoft;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Set;

import org.apache.poi.hwmf.record.HwmfFont;
import org.apache.poi.hwmf.record.HwmfRecord;
import org.apache.poi.hwmf.record.HwmfRecordType;
import org.apache.poi.hwmf.record.HwmfText;
import org.apache.poi.hwmf.usermodel.HwmfPicture;
import org.apache.poi.util.LocaleUtil;
import org.apache.poi.util.RecordFormatException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RenderingParser;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * This parser offers a very rough capability to extract text if there
 * is text stored in the WMF files.
 */
/**
 * Extracts the text of a WMF image. With
 * {@link MetafileParserConfig#setRenderImage(boolean)}
 * ("wmf-parser": {"renderImage": true}) the image is also rendered through the
 * configured {@link Renderer}, the
 * {@link org.apache.tika.renderer.microsoft.POIMetafileRenderer} by
 * default, and emitted as a
 * {@link TikaCoreProperties.EmbeddedResourceType#RENDERING} embedded document,
 * the way the PDF parser emits page renderings.
 */
@TikaComponent
public class WMFParser implements Parser, RenderingParser {

    private static final MediaType MEDIA_TYPE = MediaType.image("wmf");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MEDIA_TYPE);

    private final MetafileParserConfig defaultConfig;
    private Renderer renderer;

    public WMFParser() {
        this(new MetafileParserConfig());
    }

    public WMFParser(MetafileParserConfig config) {
        this.defaultConfig = config;
    }

    public WMFParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, MetafileParserConfig.class));
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        tis.setCloseShield();
        try {
            HwmfPicture picture = null;
            try {
                picture = new HwmfPicture(tis);
            } catch (ArrayIndexOutOfBoundsException e) {
                //POI can throw this on corrupt files
                throw new TikaException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            }
            Charset charset = LocaleUtil.CHARSET_1252;
            //TODO: make x/y info public in POI so that we can use it here
            //to determine when to keep two text parts on the same line
            for (HwmfRecord record : picture.getRecords()) {
                //this is pure hackery for specifying the font
                //TODO: do what Graphics does by maintaining the stack, etc.!
                //This fix should be done within POI
                if (record.getWmfRecordType().equals(HwmfRecordType.createFontIndirect)) {
                    HwmfFont font = ((HwmfText.WmfCreateFontIndirect) record).getFont();
                    charset =
                            (font.getCharset() == null || font.getCharset().getCharset() == null) ?
                                    LocaleUtil.CHARSET_1252 : font.getCharset().getCharset();
                }
                if (record.getWmfRecordType().equals(HwmfRecordType.extTextOut)) {
                    HwmfText.WmfExtTextOut textOut = (HwmfText.WmfExtTextOut) record;
                    xhtml.startElement("p");
                    xhtml.characters(textOut.getText(charset));
                    xhtml.endElement("p");
                } else if (record.getWmfRecordType().equals(HwmfRecordType.textOut)) {
                    HwmfText.WmfTextOut textOut = (HwmfText.WmfTextOut) record;
                    xhtml.startElement("p");
                    xhtml.characters(textOut.getText(charset));
                    xhtml.endElement("p");
                }
            }
            MetafileParserConfig config = getConfig(context);
            if (config.isRenderImage()) {
                MetafileRendering.render(renderer, config, MEDIA_TYPE, picture, xhtml, metadata,
                        context);
            }
        } catch (RecordFormatException e) { //POI's hwmfparser can \ throw these for "parse
            // exceptions"
            throw new TikaException(e.getMessage(), e);
        } catch (RuntimeException e) { //convert Runtime to RecordFormatExceptions
            throw new TikaException(e.getMessage(), e);
        } catch (AssertionError e) { //POI's hwmfparser can throw these for parse exceptions
            throw new TikaException(e.getMessage(), e);
        } finally {
            tis.removeCloseShield();
        }
        xhtml.endDocument();
    }

    private MetafileParserConfig getConfig(ParseContext context)
            throws TikaException, IOException {
        return ParseContextConfig.getConfig(context, "wmf-parser",
                MetafileParserConfig.class, defaultConfig);
    }

    @Override
    public void setRenderer(Renderer renderer) {
        this.renderer = renderer;
    }

    public Renderer getRenderer() {
        return renderer;
    }
}
