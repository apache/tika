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
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

public class ImageParser extends AbstractImageParser {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 7852529269245520335L;

    private static final Logger LOG = LoggerFactory.getLogger(ImageParser.class);

    private static final MediaType MAIN_BMP_TYPE = MediaType.image("bmp");
    private static final MediaType OLD_BMP_TYPE = MediaType.image("x-ms-bmp");

    private static final Set<MediaType> TMP_SUPPORTED;

    static {
        TMP_SUPPORTED = new HashSet<>(
                Arrays.asList(MAIN_BMP_TYPE, OLD_BMP_TYPE, MediaType.image("gif"),
                        MediaType.image("png"), MediaType.image("vnd.wap.wbmp"),
                        MediaType.image("x-icon"), MediaType.image("x-xcf"),
                        MediaType.image("x-jbig2")));
        //add try/catch class.forName() for image types relying on
        //provided dependencies
    }

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(TMP_SUPPORTED);

    private static void setIfPresent(Metadata metadata, String imageIOkey, String tikaKey) {
        if (metadata.get(imageIOkey) != null) {
            metadata.set(tikaKey, metadata.get(imageIOkey));
        }
    }

    private static void setIfPresent(Metadata metadata, String imageIOkey, Property tikaProp) {
        if (metadata.get(imageIOkey) != null) {
            String v = metadata.get(imageIOkey);
            if (v.endsWith(" ")) {
                v = v.substring(0, v.lastIndexOf(' '));
            }
            metadata.set(tikaProp, v);
        }
    }

    private static void loadMetadata(IIOMetadata imageMetadata, Metadata metadata) {
        if (imageMetadata == null) {
            return;
        }
        String[] names = imageMetadata.getMetadataFormatNames();
        if (names == null) {
            return;
        }
        for (String name : names) {
            loadNode(metadata, imageMetadata.getAsTree(name), "", false);
        }
    }

    private static void loadNode(Metadata metadata, Node node, String parents,
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
                metadata.add(parents, normalize(map.item(0).getNodeValue()));
            } else if (length > 1) {
                StringBuilder value = new StringBuilder();
                for (int i = 0; i < length; i++) {
                    if (i > 0) {
                        value.append(", ");
                    }
                    Node attr = map.item(i);
                    value.append(attr.getNodeName());
                    value.append("=");
                    value.append(normalize(attr.getNodeValue()));
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

    private static String normalize(String value) {
        if (value != null) {
            value = value.trim();
        } else {
            value = "";
        }
        if (Boolean.TRUE.toString().equalsIgnoreCase(value)) {
            return Boolean.TRUE.toString();
        } else if (Boolean.FALSE.toString().equalsIgnoreCase(value)) {
            return Boolean.FALSE.toString();
        }
        return value;
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }


    @Override
    void extractMetadata(InputStream stream, ContentHandler contentHandler, Metadata metadata,
                         ParseContext parseContext)
            throws IOException, SAXException, TikaException {
        String type = metadata.get(Metadata.CONTENT_TYPE);
        if (type == null) {
            return;
        }
        try {
            Iterator<ImageReader> iterator = ImageIO.getImageReadersByMIMEType(type);
            if (iterator.hasNext()) {
                ImageReader reader = iterator.next();
                try {
                    try (ImageInputStream imageStream = ImageIO
                            .createImageInputStream(CloseShieldInputStream.wrap(stream))) {
                        reader.setInput(imageStream);

                        metadata.set(Metadata.IMAGE_WIDTH, Integer.toString(reader.getWidth(0)));
                        metadata.set(Metadata.IMAGE_LENGTH, Integer.toString(reader.getHeight(0)));
                        metadata.set("height", Integer.toString(reader.getHeight(0)));
                        metadata.set("width", Integer.toString(reader.getWidth(0)));

                        loadMetadata(reader.getImageMetadata(0), metadata);
                    }
                } finally {
                    reader.dispose();
                }
            }

            // Translate certain Metadata tags from the ImageIO
            //  specific namespace into the general Tika one
            setIfPresent(metadata, "CommentExtensions CommentExtension",
                    TikaCoreProperties.COMMENTS);
            setIfPresent(metadata, "markerSequence com", TikaCoreProperties.COMMENTS);
            setIfPresent(metadata, "Data BitsPerSample", Metadata.BITS_PER_SAMPLE);
        } catch (IIOException e) {
            // TIKA-619: There is a known bug in the Sun API when dealing with GIF images
            //  which Tika will just ignore.
            if (!(e.getMessage() != null && e.getMessage().equals("Unexpected block type 0!") &&
                    type.equals("image/gif"))) {
                throw new TikaException(type + " parse error", e);
            }
        }
    }

    @Override
    MediaType normalizeMediaType(MediaType mediaType) {

        if (mediaType == null) {
            return null;
        }
        // If the old (pre-RFC7903) BMP mime type is given,
        //  fix it up to the new one, so Java is happy
        if (OLD_BMP_TYPE.equals(mediaType)) {
            return MAIN_BMP_TYPE;
        }
        return mediaType;
    }
}
