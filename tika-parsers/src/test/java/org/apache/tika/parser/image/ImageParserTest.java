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

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.helpers.DefaultHandler;

import junit.framework.TestCase;

public class ImageParserTest extends TestCase {

    private final Parser parser = new ImageParser();

    public void testBMP() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/bmp");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testBMP.bmp");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("75", metadata.get("height"));
        assertEquals("100", metadata.get("width"));
        assertEquals("8 8 8", metadata.get("Data BitsPerSample"));
        assertEquals("1.0", metadata.get("Dimension PixelAspectRatio"));
        assertEquals("0", metadata.get("Dimension VerticalPhysicalPixelSpacing"));
        assertEquals("0", metadata.get("Dimension HorizontalPhysicalPixelSpacing"));
        assertEquals("BI_RGB", metadata.get("Compression CompressionTypeName"));
        assertEquals("image/bmp", metadata.get("Content-Type"));
        
        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8 8 8", metadata.get(Metadata.BITS_PER_SAMPLE));
    }

    public void testGIF() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/gif");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testGIF.gif");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("75", metadata.get("height"));
        assertEquals("100", metadata.get("width"));
        assertEquals("true", metadata.get("Compression Lossless"));
        assertEquals("Normal", metadata.get("Dimension ImageOrientation"));
        assertEquals("lzw", metadata.get("Compression CompressionTypeName"));
        assertEquals("0", metadata.get("Dimension HorizontalPixelOffset"));
        assertEquals("imageLeftPosition=0, imageTopPosition=0, imageWidth=100, imageHeight=75, interlaceFlag=false", metadata.get("ImageDescriptor"));
        assertEquals("Index", metadata.get("Data SampleFormat"));
        assertEquals("3", metadata.get("Chroma NumChannels"));
        assertEquals("1", metadata.get("Compression NumProgressiveScans"));
        assertEquals("RGB", metadata.get("Chroma ColorSpaceType"));
        assertEquals("Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.", metadata.get("CommentExtensions CommentExtension"));
        assertEquals("value=Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership., encoding=ISO-8859-1, compression=none", metadata.get("Text TextEntry"));
        assertEquals("true", metadata.get("Chroma BlackIsZero"));
        assertEquals("disposalMethod=none, userInputFlag=false, transparentColorFlag=false, delayTime=0, transparentColorIndex=0", metadata.get("GraphicControlExtension"));
        assertEquals("0", metadata.get("Dimension VerticalPixelOffset"));
        assertEquals("image/gif", metadata.get("Content-Type"));
        
        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.", metadata.get(TikaCoreProperties.COMMENTS));
    }

    public void testJPEG() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG.jpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("75", metadata.get("height"));
        assertEquals("100", metadata.get("width"));
        assertEquals("0.35277778", metadata.get("Dimension VerticalPixelSize"));
        assertEquals("false", metadata.get("Compression Lossless"));
        assertEquals("class=0, htableId=0", metadata.get("markerSequence dht dhtable"));
        assertEquals("majorVersion=1, minorVersion=1, resUnits=1, Xdensity=72, Ydensity=72, thumbWidth=0, thumbHeight=0", metadata.get("JPEGvariety app0JFIF"));
        assertEquals("225", metadata.get("markerSequence unknown"));
        assertEquals("componentSelector=1, dcHuffTable=0, acHuffTable=0", metadata.get("markerSequence sos scanComponentSpec"));
        assertEquals("normal", metadata.get("Dimension ImageOrientation"));
        assertEquals("1.0", metadata.get("Dimension PixelAspectRatio"));
        assertEquals("elementPrecision=0, qtableId=0", metadata.get("markerSequence dqt dqtable"));
        assertEquals("numScanComponents=3, startSpectralSelection=0, endSpectralSelection=63, approxHigh=0, approxLow=0", metadata.get("markerSequence sos"));
        assertEquals("componentId=1, HsamplingFactor=1, VsamplingFactor=1, QtableSelector=0", metadata.get("markerSequence sof componentSpec"));
        assertEquals("JPEG", metadata.get("Compression CompressionTypeName"));
        assertEquals("0.35277778", metadata.get("Dimension HorizontalPixelSize"));
        assertEquals("Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.", metadata.get("markerSequence com"));
        assertEquals("3", metadata.get("Chroma NumChannels"));
        assertEquals("1", metadata.get("Compression NumProgressiveScans"));
        assertEquals("YCbCr", metadata.get("Chroma ColorSpaceType"));
        assertEquals("keyword=comment, value=Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.", metadata.get("Text TextEntry"));
        assertEquals("image/jpeg", metadata.get("Content-Type"));
        assertEquals("process=0, samplePrecision=8, numLines=75, samplesPerLine=100, numFrameComponents=3", metadata.get("markerSequence sof"));
        
        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.", metadata.get(TikaCoreProperties.COMMENTS));
    }

    public void testPNG() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testPNG.png");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("75", metadata.get("height"));
        assertEquals("100", metadata.get("width"));
        assertEquals("0.35273367", metadata.get("Dimension VerticalPixelSize"));
        assertEquals("8 8 8", metadata.get("Data BitsPerSample"));
        assertEquals("Perceptual", metadata.get("sRGB"));
        assertEquals("true", metadata.get("Compression Lossless"));
        assertEquals("year=2008, month=5, day=6, hour=6, minute=18, second=47", metadata.get("tIME"));
        assertEquals("Normal", metadata.get("Dimension ImageOrientation"));
        assertEquals("1.0", metadata.get("Dimension PixelAspectRatio"));
        assertEquals("keyword=Comment, value=Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.", metadata.get("tEXt tEXtEntry"));
        assertEquals("deflate", metadata.get("Compression CompressionTypeName"));
        assertEquals("UnsignedIntegral", metadata.get("Data SampleFormat"));
        assertEquals("0.35273367", metadata.get("Dimension HorizontalPixelSize"));
        assertEquals("none", metadata.get("Transparency Alpha"));
        assertEquals("pixelsPerUnitXAxis=2835, pixelsPerUnitYAxis=2835, unitSpecifier=meter", metadata.get("pHYs"));
        assertEquals("3", metadata.get("Chroma NumChannels"));
        assertEquals("1", metadata.get("Compression NumProgressiveScans"));
        assertEquals("RGB", metadata.get("Chroma ColorSpaceType"));
        assertEquals("keyword=Comment, value=Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership., encoding=ISO-8859-1, compression=none", metadata.get("Text TextEntry"));
        assertEquals("PixelInterleaved", metadata.get("Data PlanarConfiguration"));
        assertEquals("width=100, height=75, bitDepth=8, colorType=RGB, compressionMethod=deflate, filterMethod=adaptive, interlaceMethod=none", metadata.get("IHDR"));
        assertEquals("true", metadata.get("Chroma BlackIsZero"));
        assertEquals("year=2008, month=5, day=6, hour=6, minute=18, second=47", metadata.get("Document ImageModificationTime"));
        assertEquals("image/png", metadata.get("Content-Type"));
        
        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8 8 8", metadata.get(Metadata.BITS_PER_SAMPLE));
    }

}
