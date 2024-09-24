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
package org.apache.tika.parser.pkg;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.UnsupportedFormatException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for universal executable files.
 */
public class UniversalExecutableParser implements Parser {
    private static final long serialVersionUID = 1L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("x-mach-o-universal"));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        byte[] first4 = new byte[4];
        IOUtils.readFully(stream, first4);

        if ((first4[0] == (byte) 0xBF || first4[0] == (byte) 0xBE) &&
                first4[1] == (byte) 0xBA && first4[2] == (byte) 0xFE && first4[3] == (byte) 0xCA) {
            parseMachO(xhtml, extractor, handler, metadata, stream, first4);
        } else if (first4[0] == (byte) 0xCA && first4[1] == (byte) 0xFE &&
                first4[2] == (byte) 0xBA &&
                (first4[3] == (byte) 0xBF || first4[3] == (byte) 0xBE)) {
            parseMachO(xhtml, extractor, handler, metadata, stream, first4);
        } else {
            throw new UnsupportedFormatException("Not a universal executable file");
        }

        xhtml.endDocument();
    }

    /**
     * Parses a Mach-O Universal file
     */
    public void parseMachO(XHTMLContentHandler xhtml, EmbeddedDocumentExtractor extractor,
                           ContentHandler handler, Metadata metadata, InputStream stream,
                           byte[] first4)
            throws IOException, SAXException, TikaException {
        var currentOffset = (long) first4.length;
        var isLE = first4[3] == (byte) 0xCA;
        var is64 = first4[isLE ? 0 : 3] == (byte) 0xBF;
        var archStructSize = 4 /* cputype */ + 4 /* cpusubtype */ + (is64
                ? 8 /* offset */ + 8 /* size */ + 4 /* align */ + 4 /* reserved */
                : 4 /* offset */ + 4 /* size */ + 4 /* align */);

        var archsCount = isLE ? EndianUtils.readIntLE(stream) : EndianUtils.readIntBE(stream);
        if (archsCount < 1) {
            throw new TikaException("Invalid number of architectures: " + archsCount);
        }
        currentOffset += 4;

        var archsSize = archsCount * archStructSize;

        var unsortedOffsets = false;
        var offsetAndSizePerArch = new Pair[archsCount];
        for (var archIndex = 0; archIndex < archsCount; archIndex++) {
            if (stream.skip(8) != 8) {
                throw new TikaException("Failed to skip cputype and cpusubtype");
            }

            var offset = is64
                    ? (isLE ? EndianUtils.readLongLE(stream) : EndianUtils.readLongBE(stream))
                    : (isLE ? EndianUtils.readIntLE(stream) : EndianUtils.readIntBE(stream));
            if (offset < 4 + 4 + archsSize) {
                throw new TikaException("Invalid offset: " + offset);
            }
            if (!unsortedOffsets && archIndex > 0 && offset < (long) offsetAndSizePerArch[archIndex - 1].getLeft()) {
                unsortedOffsets = true;
            }
            var size = is64
                    ? (isLE ? EndianUtils.readLongLE(stream) : EndianUtils.readLongBE(stream))
                    : (isLE ? EndianUtils.readIntLE(stream) : EndianUtils.readIntBE(stream));

            offsetAndSizePerArch[archIndex] = Pair.of(offset, size);

            if (is64) {
                if (stream.skip(8) != 8) {
                    throw new TikaException("Failed to skip align and reserved");
                }
            } else {
                if (stream.skip(4) != 4) {
                    throw new TikaException("Failed to skip align");
                }
            }

            currentOffset += archStructSize;
        }
        if (unsortedOffsets) {
            Arrays.sort(offsetAndSizePerArch, Comparator.comparingLong(entry -> (long) entry.getLeft()));
        }

        for (var archIndex = 0; archIndex < archsCount; archIndex++) {
            var skipUntilStart = (long) offsetAndSizePerArch[archIndex].getLeft() - currentOffset;
            if (stream.skip(skipUntilStart) != skipUntilStart) {
                throw new TikaException("Failed to skip to the start of the per-architecture Mach-O");
            }
            currentOffset += skipUntilStart;

            var perArchMachO = new byte[(int) (long) offsetAndSizePerArch[archIndex].getRight()];
            if (stream.read(perArchMachO) != perArchMachO.length) {
                throw new TikaException("Failed to read the per-architecture Mach-O");
            }
            currentOffset += perArchMachO.length;

            var perArchMetadata = new Metadata();
            var tikaInputStream = TikaInputStream.get(perArchMachO, perArchMetadata);
            if (extractor.shouldParseEmbedded(perArchMetadata)) {
                extractor.parseEmbedded(tikaInputStream, handler, perArchMetadata, true);
            }
        }
    }
}
