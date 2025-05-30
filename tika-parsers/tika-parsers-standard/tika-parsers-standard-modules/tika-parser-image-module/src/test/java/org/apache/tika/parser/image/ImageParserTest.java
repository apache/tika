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
package org.apache.tika.parser.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class ImageParserTest extends TikaTest {

    private final Parser parser = new ImageParser();

    @Test
    public void testBMP() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/bmp");
        try (InputStream stream = getResourceAsStream("/test-documents/testBMP.bmp")) {
            parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }

        assertEquals("75", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "height"));
        assertEquals("100", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "width"));
        assertEquals("8 8 8", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Data BitsPerSample"));
        assertEquals("1.0", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Dimension PixelAspectRatio"));
        //TODO: figure out why we're getting 0.35273367 in Ubuntu, but not Windows
        //assertEquals("0", metadata.get("Dimension VerticalPhysicalPixelSpacing"));
        //assertEquals("0", metadata.get("Dimension HorizontalPhysicalPixelSpacing"));
        assertEquals("BI_RGB", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Compression CompressionTypeName"));
        assertEquals("image/bmp", metadata.get("Content-Type"));

        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8 8 8", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals(1, metadata.getInt(TikaCoreProperties.NUM_IMAGES));
    }

    @Test
    public void testGIF() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/gif");
        try (InputStream stream = getResourceAsStream("/test-documents/testGIF.gif")) {
            parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }

        assertEquals("75", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "height"));
        assertEquals("100", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "width"));
        assertEquals("true", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Compression Lossless"));
        assertEquals("Normal", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Dimension ImageOrientation"));
        assertEquals("lzw", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Compression CompressionTypeName"));
        assertEquals("0", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Dimension HorizontalPixelOffset"));
        assertEquals("imageLeftPosition=0, imageTopPosition=0, imageWidth=100, " +
                "imageHeight=75, interlaceFlag=false", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "ImageDescriptor"));
        assertEquals("Index", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Data SampleFormat"));
        assertEquals("3", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Chroma NumChannels"));
        assertEquals("1", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Compression NumProgressiveScans"));
        assertEquals("RGB", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Chroma ColorSpaceType"));
        assertEquals("Licensed to the Apache Software Foundation (ASF) under " +
                "one or more contributor license agreements.  See the NOTICE file " +
                "distributed with this work for additional information regarding " +
                "copyright ownership.", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "CommentExtensions CommentExtension"));
        assertEquals("value=Licensed to the Apache Software Foundation (ASF) under one " +
                        "or more contributor license agreements.  See the NOTICE file " +
                        "distributed with this work for additional information regarding " +
                        "copyright ownership., encoding=ISO-8859-1, compression=none",
                metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Text TextEntry"));
        assertEquals("true", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Chroma BlackIsZero"));
        assertEquals("disposalMethod=none, userInputFlag=false, transparentColorFlag=false, " +
                "delayTime=0, transparentColorIndex=0", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "GraphicControlExtension"));
        assertEquals("0", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Dimension VerticalPixelOffset"));
        assertEquals("image/gif", metadata.get("Content-Type"));

        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("Licensed to the Apache Software Foundation (ASF) under one or " +
                        "more contributor license agreements.  See the NOTICE file distributed " +
                        "with this work for additional information regarding copyright ownership.",
                metadata.get(TikaCoreProperties.COMMENTS));
        assertEquals(1, metadata.getInt(TikaCoreProperties.NUM_IMAGES));
    }

    @Test
    public void testJPEG() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        try (InputStream stream = getResourceAsStream("/test-documents/testJPEG.jpg")) {
            parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }

        assertEquals("75", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "height"));
        assertEquals("100", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "width"));
        assertEquals("0.35277778", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Dimension VerticalPixelSize"));
        assertEquals("false", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Compression Lossless"));
        assertEquals("class=0, htableId=0", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "markerSequence dht dhtable"));
        assertEquals("majorVersion=1, minorVersion=1, resUnits=1, Xdensity=72, " +
                "Ydensity=72, thumbWidth=0, thumbHeight=0", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "JPEGvariety app0JFIF"));
        assertEquals("225", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "markerSequence unknown"));
        assertEquals("componentSelector=1, dcHuffTable=0, acHuffTable=0",
                metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "markerSequence sos scanComponentSpec"));
        assertEquals("normal", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Dimension ImageOrientation"));
        assertEquals("1.0", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Dimension PixelAspectRatio"));
        assertEquals("elementPrecision=0, qtableId=0", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "markerSequence dqt dqtable"));
        assertEquals("numScanComponents=3, startSpectralSelection=0, " +
                        "endSpectralSelection=63, approxHigh=0, approxLow=0",
                metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "markerSequence sos"));
        assertEquals("componentId=1, HsamplingFactor=1, " + "VsamplingFactor=1, QtableSelector=0",
                metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "markerSequence sof componentSpec"));
        assertEquals("JPEG", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Compression CompressionTypeName"));
        assertEquals("0.35277778", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Dimension HorizontalPixelSize"));
        assertEquals("Licensed to the Apache Software Foundation (ASF) under one or " +
                "more contributor license agreements.  See the NOTICE file " +
                "distributed with this work for additional information " +
                "regarding copyright ownership.", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "markerSequence com"));
        assertEquals("3", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Chroma NumChannels"));
        assertEquals("1", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Compression NumProgressiveScans"));
        assertEquals("YCbCr", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Chroma ColorSpaceType"));
        assertEquals("keyword=comment, value=Licensed to the Apache Software Foundation " +
                "(ASF) under one or more contributor license agreements.  See the NOTICE" +
                " file distributed with this work for additional information regarding " +
                "copyright ownership.", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Text TextEntry"));
        assertEquals("image/jpeg", metadata.get("Content-Type"));
        assertEquals("process=0, samplePrecision=8, numLines=75, samplesPerLine=100, " +
                "numFrameComponents=3", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "markerSequence sof"));

        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("Licensed to the Apache Software Foundation (ASF) under one or " +
                        "more contributor license agreements.  See the NOTICE file distributed " +
                        "with this work for additional information regarding copyright ownership.",
                metadata.get(TikaCoreProperties.COMMENTS));
        assertEquals(1, metadata.getInt(TikaCoreProperties.NUM_IMAGES));
    }

    @Test
    public void testPNG() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");
        try (InputStream stream = getResourceAsStream("/test-documents/testPNG.png")) {
            parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }

        assertEquals("75", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "height"));
        assertEquals("100", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "width"));
        assertEquals("0.35273367", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Dimension VerticalPixelSize"));
        assertEquals("8 8 8", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Data BitsPerSample"));
        assertEquals("Perceptual", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "sRGB"));
        assertEquals("true", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Compression Lossless"));
        assertEquals("year=2008, month=5, day=6, hour=6, minute=18, second=47",
                metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "tIME"));
        assertEquals("Normal", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Dimension ImageOrientation"));
        assertEquals("1.0", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Dimension PixelAspectRatio"));
        assertEquals("keyword=Comment, value=Licensed to the Apache Software Foundation " +
                "(ASF) under one or more contributor license agreements.  See the " +
                "NOTICE file distributed with this work for additional information " +
                "regarding copyright ownership.", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "tEXt tEXtEntry"));
        assertEquals("deflate", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Compression CompressionTypeName"));
        assertEquals("UnsignedIntegral", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Data SampleFormat"));
        assertEquals("0.35273367", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Dimension HorizontalPixelSize"));
        assertEquals("none", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Transparency Alpha"));
        assertEquals("pixelsPerUnitXAxis=2835, pixelsPerUnitYAxis=2835, unitSpecifier=meter",
                metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "pHYs"));
        assertEquals("3", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Chroma NumChannels"));
        assertEquals("1", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Compression NumProgressiveScans"));
        assertEquals("RGB", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Chroma ColorSpaceType"));
        assertEquals("keyword=Comment, value=Licensed to the Apache Software Foundation " +
                        "(ASF) under one or more contributor license agreements.  See the " +
                        "NOTICE file distributed with this work for additional information " +
                        "regarding copyright ownership., encoding=ISO-8859-1, compression=none",
                metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Text TextEntry"));
        assertEquals("PixelInterleaved", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Data PlanarConfiguration"));
        assertEquals("width=100, height=75, bitDepth=8, colorType=RGB, " +
                        "compressionMethod=deflate, filterMethod=adaptive, interlaceMethod=none",
                metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "IHDR"));
        assertEquals("true", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Chroma BlackIsZero"));
        assertEquals("year=2008, month=5, day=6, hour=6, minute=18, second=47",
                metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Document ImageModificationTime"));
        assertEquals("image/png", metadata.get("Content-Type"));

        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8 8 8", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals(1, metadata.getInt(TikaCoreProperties.NUM_IMAGES));
    }

    @Test // TIKA-2232
    public void testJBIG2() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/x-jbig2");
        try (InputStream stream = getResourceAsStream("/test-documents/testJBIG2.jb2")) {
            parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }
        assertEquals("78", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "height"));
        assertEquals("328", metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "width"));
        assertEquals("image/x-jbig2", metadata.get("Content-Type"));
        assertEquals(1, metadata.getInt(TikaCoreProperties.NUM_IMAGES));
    }

    @Test
    public void testMimeTypeToOCRMimeTypeConversion() throws Exception {
        assertEquals(new MediaType("image", "OCR-png"),
                AbstractImageParser.convertToOCRMediaType(MediaType.image("png")));
    }

    @Test
    public void testNPEOnEmptyContentType() throws Exception {
        //test no NPE TIKA-3569
        Metadata metadata = new Metadata();
        try (InputStream stream = getResourceAsStream("/test-documents/testBMP.bmp")) {
            parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }

        metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "unparseablegarbage");
        try (InputStream stream = getResourceAsStream("/test-documents/testBMP.bmp")) {
            parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }
    }
}
