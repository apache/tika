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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ImageParser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                MediaType.image("bmp"),
                MediaType.image("gif"),
                MediaType.image("png"),
                MediaType.image("vnd.wap.wbmp"),
                MediaType.image("x-icon"),
                MediaType.image("x-psd"),
                MediaType.image("x-xcf"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        String type = metadata.get(Metadata.CONTENT_TYPE);
        if (type != null) {
            try {
                Iterator<ImageReader> iterator =
                    ImageIO.getImageReadersByMIMEType(type);
                if (iterator.hasNext()) {
                    ImageReader reader = iterator.next();
                    reader.setInput(ImageIO.createImageInputStream(
                            new CloseShieldInputStream(stream)));
                    
                    metadata.set(Metadata.IMAGE_WIDTH, Integer.toString(reader.getWidth(0)));
                    metadata.set(Metadata.IMAGE_LENGTH, Integer.toString(reader.getHeight(0)));
                    metadata.set("height", Integer.toString(reader.getHeight(0)));
                    metadata.set("width", Integer.toString(reader.getWidth(0)));

                    loadMetadata(reader.getImageMetadata(0), metadata);

                    reader.dispose();
                }
                
                // Translate certain Metadata tags from the ImageIO
                //  specific namespace into the general Tika one
                setIfPresent(metadata, "CommentExtensions CommentExtension", Metadata.COMMENTS);
                setIfPresent(metadata, "markerSequence com", Metadata.COMMENTS);
                setIfPresent(metadata, "Data BitsPerSample", Metadata.BITS_PER_SAMPLE);
            } catch (IIOException e) {
                throw new TikaException(type + " parse error", e);
            }
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }
    
    private static void setIfPresent(Metadata metadata, String imageIOkey, String tikaKey) {
	if(metadata.get(imageIOkey) != null) {
	    metadata.set(tikaKey, metadata.get(imageIOkey));
	}
    }
    private static void setIfPresent(Metadata metadata, String imageIOkey, Property tikaProp) {
	if(metadata.get(imageIOkey) != null) {
	    String v = metadata.get(imageIOkey);
	    if(v.endsWith(" ")) {
		v = v.substring(0, v.lastIndexOf(' '));
	    }
	    metadata.set(tikaProp, v);
	}
    }

    private static void loadMetadata(IIOMetadata imageMetadata, Metadata metadata) {
        String[] names = imageMetadata.getMetadataFormatNames();
        if (names == null) {
            return;
        }
        int length = names.length;
        for (int i = 0; i < length; i++) {
            loadNode(metadata, imageMetadata.getAsTree(names[i]), "", false);
        }
    }

    private static void loadNode(
            Metadata metadata, Node node, String parents,
            boolean addThisNodeName) {
        if (addThisNodeName) {
            if (parents.length() > 0) {
                parents += " ";
            }
            parents += node.getNodeName();
        }
        NamedNodeMap map = node.getAttributes();
        if (map != null) {

            int length = map.getLength();
            if (length == 1) {
                metadata.add(parents, map.item(0).getNodeValue());
            } else if (length > 1) {
                StringBuffer value = new StringBuffer();
                for (int i = 0; i < length; i++) {
                    if (i > 0) {
                        value.append(", ");
                    }
                    Node attr = map.item(i);
                    value.append(attr.getNodeName()).append("=").append(attr.getNodeValue());
                }
                metadata.add(parents, value.toString());
            }
        }

        Node child = node.getFirstChild();
        while (child != null) {
            // print children recursively
            loadNode(metadata, child, parents, true);
            child = child.getNextSibling();
        }
    }

}
