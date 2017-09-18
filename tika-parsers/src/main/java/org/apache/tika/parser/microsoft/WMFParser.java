/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.parser.microsoft;

import java.io.IOException;
import java.io.InputStream;
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
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This parser offers a very rough capability to extract text if there
 * is text stored in the WMF files.
 */
public class WMFParser extends AbstractParser {

    private static final MediaType MEDIA_TYPE = MediaType.image("wmf");

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MEDIA_TYPE);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        try {
            HwmfPicture picture = new HwmfPicture(stream);
            //TODO: make x/y info public in POI so that we can use it here
            //to determine when to keep two text parts on the same line
            for (HwmfRecord record : picture.getRecords()) {
                Charset charset = LocaleUtil.CHARSET_1252;
                //this is pure hackery for specifying the font
                //TODO: do what Graphics does by maintaining the stack, etc.!
                //This fix should be done within POI
                if (record.getRecordType().equals(HwmfRecordType.createFontIndirect)) {
                    HwmfFont font = ((HwmfText.WmfCreateFontIndirect) record).getFont();
                    charset = (font.getCharset() == null || font.getCharset().getCharset() == null)
                            ? LocaleUtil.CHARSET_1252 :
                            font.getCharset().getCharset();
                }
                if (record.getRecordType().equals(HwmfRecordType.extTextOut)) {
                    HwmfText.WmfExtTextOut textOut = (HwmfText.WmfExtTextOut) record;
                    xhtml.startElement("p");
                    xhtml.characters(textOut.getText(charset));
                    xhtml.endElement("p");
                } else if (record.getRecordType().equals(HwmfRecordType.textOut)) {
                    HwmfText.WmfTextOut textOut = (HwmfText.WmfTextOut) record;
                    xhtml.startElement("p");
                    xhtml.characters(textOut.getText(charset));
                    xhtml.endElement("p");
                }
            }
        } catch (RecordFormatException e) { //POI's hwmfparser can throw these for "parse exceptions"
            throw new TikaException(e.getMessage(), e);
        } catch (RuntimeException e) { //convert Runtime to RecordFormatExceptions
            throw new TikaException(e.getMessage(), e);
        } catch (AssertionError e) { //POI's hwmfparser can throw these for parse exceptions
            throw new TikaException(e.getMessage(), e);
        }
        xhtml.endDocument();
    }

}
