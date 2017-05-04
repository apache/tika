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
package org.apache.tika.parser.apple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser that strips the header off of AppleSingle and AppleDouble
 * files.
 * <p>
 * See <a href="http://kaiser-edv.de/documents/AppleSingle_AppleDouble.pdf">spec document</a>.
 */
public class AppleSingleFileParser extends AbstractParser {

    /**
     * Entry types
     */
    private static final int DATA_FORK = 1;
    private static final int RESOURCE_FORK = 2;
    private static final int REAL_NAME = 3;
    private static final int COMMENT = 4;
    private static final int ICON_BW = 5;
    private static final int ICON_COLOR = 6;
    //7?!
    private static final int FILE_DATES_INFO = 8;
    private static final int FINDER_INFO = 9;
    private static final int MACINTOSH_FILE_INFO = 10;
    private static final int PRODOS_FILE_INFO = 11;
    private static final int MSDOS_FILE_INFO = 12;
    private static final int SHORT_NAME = 13;
    private static final int AFP_FILE_INFO = 14;
    private static final int DIRECTORY_ID = 15;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("applefile"));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor ex = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        short numEntries = readThroughNumEntries(stream);
        long bytesRead = 26;
        List<FieldInfo> fieldInfoList = getSortedFieldInfoList(stream, numEntries);
        bytesRead += 12*numEntries;
        Metadata embeddedMetadata = new Metadata();
        bytesRead = processFieldEntries(stream, fieldInfoList, embeddedMetadata, bytesRead);
        FieldInfo contentFieldInfo = getContentFieldInfo(fieldInfoList);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        if (contentFieldInfo != null) {
            long diff = contentFieldInfo.offset-bytesRead;
            IOUtils.skipFully(stream, diff);
            if (ex.shouldParseEmbedded(embeddedMetadata)) {
                // TODO: we should probably add a readlimiting wrapper around this
                // stream to ensure that not more than contentFieldInfo.length bytes
                // are read
                ex.parseEmbedded(new CloseShieldInputStream(stream),
                        xhtml, embeddedMetadata, false);
            }
        }
        xhtml.endDocument();

    }

    private FieldInfo getContentFieldInfo(List<FieldInfo> fieldInfoList) {
        for (FieldInfo fieldInfo : fieldInfoList) {
            if (fieldInfo.entryId == 1) {
                return fieldInfo;
            }
        }
        return null;
    }

    private long processFieldEntries(InputStream stream, List<FieldInfo> fieldInfoList,
                                     Metadata embeddedMetadata, long bytesRead) throws IOException, TikaException {
        byte[] buffer = null;
        for (FieldInfo f : fieldInfoList) {
            long diff = f.offset - bytesRead;
            //just in case
            IOUtils.skipFully(stream, diff);
            bytesRead += diff;
            if (f.entryId == REAL_NAME) {
                if (f.length > Integer.MAX_VALUE) {
                    throw new TikaException("File name length can't be > integer max");
                }
                buffer = new byte[(int)f.length];
                IOUtils.readFully(stream, buffer);
                bytesRead += f.length;
                String originalFileName = new String(buffer, 0, buffer.length, StandardCharsets.US_ASCII);
                embeddedMetadata.set(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, originalFileName);
            } else if (f.entryId != DATA_FORK) {
                IOUtils.skipFully(stream, f.length);
                bytesRead += f.length;
            }
        }
        return bytesRead;
    }


    private List<FieldInfo> getSortedFieldInfoList(InputStream stream, short numEntries) throws IOException, TikaException {
        //this is probably overkill.  I'd hope that these were already
        //in order.  This ensures it.
        List<FieldInfo> fieldInfoList = new ArrayList<>(numEntries);
        for (int i = 0; i < numEntries; i++) {
            //convert 32-bit unsigned ints to longs
            fieldInfoList.add(
                    new FieldInfo(
                            EndianUtils.readUIntBE(stream), //entry id
                            EndianUtils.readUIntBE(stream), //offset
                            EndianUtils.readUIntBE(stream) //length
                    )
            );
        }
        if (fieldInfoList.size() == 0) {
            throw new TikaException("AppleSingleFile missing field info");
        }
        //make absolutely sure these are in order!
        Collections.sort(fieldInfoList, new FieldInfoComparator());
        return fieldInfoList;
    }

    //read through header until you hit the number of entries
    private short readThroughNumEntries(InputStream stream) throws TikaException, IOException {
        //mime
        EndianUtils.readIntBE(stream);
        //version
        long version = EndianUtils.readIntBE(stream);
        if (version != 0x00020000) {
            throw new TikaException("Version should have been 0x00020000, but was:"+version);
        }
        IOUtils.skipFully(stream, 16);//filler
        return EndianUtils.readShortBE(stream);//number of entries
    }

    private class FieldInfo {

        private final long entryId;
        private final long offset;
        private final long length;

        private FieldInfo(long entryId, long offset, long length) {
            this.entryId = entryId;
            this.offset = offset;
            this.length = length;
        }
    }

    private static class FieldInfoComparator implements Comparator<FieldInfo> {

        @Override
        public int compare(FieldInfo o1, FieldInfo o2) {
            return (o1.offset > o2.offset) ? 1 :
                    (o1.offset == o2.offset) ? 0 : -1 ;
        }
    }

}
