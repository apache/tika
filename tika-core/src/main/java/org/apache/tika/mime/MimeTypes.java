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
package org.apache.tika.mime;

// JDK imports
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.tika.Tika;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.TextDetector;
import org.apache.tika.detect.XmlRootExtractor;
import org.apache.tika.metadata.Metadata;

/**
 * This class is a MimeType repository. It gathers a set of MimeTypes and
 * enables to retrieves a content-type from its name, from a file name, or from
 * a magic character sequence.
 * <p>
 * The MIME type detection methods that take an {@link InputStream} as
 * an argument will never reads more than {@link #getMinLength()} bytes
 * from the stream. Also the given stream is never
 * {@link InputStream#close() closed}, {@link InputStream#mark(int) marked},
 * or {@link InputStream#reset() reset} by the methods. Thus a client can
 * use the {@link InputStream#markSupported() mark feature} of the stream
 * (if available) to restore the stream back to the state it was before type
 * detection if it wants to process the stream based on the detected type.
 */
public final class MimeTypes implements Detector, Serializable {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = -1350863170146349036L;

    /**
     * Name of the {@link #rootMimeType root} type, application/octet-stream.
     */
    public static final String OCTET_STREAM = "application/octet-stream";

    /**
     * Name of the {@link #textMimeType text} type, text/plain.
     */
    public static final String PLAIN_TEXT = "text/plain";
    
    /**
     * Name of the {@link #xml xml} type, application/xml.
     */
    public static final String XML = "application/xml";

    /**
     * Root type, application/octet-stream.
     */
    private final MimeType rootMimeType;

    /**
     * Text type, text/plain.
     */
    private final MimeType textMimeType;

    /*
     * xml type, application/xml
     */
    private final MimeType xmlMimeType;

    /**
     * Registered media types and their aliases.
     */
    private final MediaTypeRegistry registry = new MediaTypeRegistry();

    /** All the registered MimeTypes indexed on their canonical names */
    private final Map<MediaType, MimeType> types =
        new HashMap<MediaType, MimeType>();

    /** The patterns matcher */
    private Patterns patterns = new Patterns(registry);

    /** Sorted list of all registered magics */
    private final List<Magic> magics = new ArrayList<Magic>();

    /** Sorted list of all registered rootXML */
    private final List<MimeType> xmls = new ArrayList<MimeType>();

    public MimeTypes() {
        rootMimeType = new MimeType(MediaType.OCTET_STREAM);
        textMimeType = new MimeType(MediaType.TEXT_PLAIN);
        xmlMimeType = new MimeType(MediaType.APPLICATION_XML);

        add(rootMimeType);
        add(textMimeType);
        add(xmlMimeType);
    }

    /**
     * Find the Mime Content Type of a document from its name.
     * Returns application/octet-stream if no better match is found.
     *
     * @deprecated Use {@link Tika#detect(String)} instead
     * @param name of the document to analyze.
     * @return the Mime Content Type of the specified document name
     */
    public MimeType getMimeType(String name) {
        MimeType type = patterns.matches(name);
        if (type != null) {
            return type;
        }
        type = patterns.matches(name.toLowerCase(Locale.ENGLISH));
        if (type != null) {
            return type;
        } else {
            return rootMimeType;
        }
    }

    /**
     * Find the Mime Content Type of a document stored in the given file.
     * Returns application/octet-stream if no better match is found.
     *
     * @deprecated Use {@link Tika#detect(File)} instead
     * @param file file to analyze
     * @return the Mime Content Type of the specified document
     * @throws MimeTypeException if the type can't be detected
     * @throws IOException if the file can't be read
     */
    public MimeType getMimeType(File file)
            throws MimeTypeException, IOException {
        return forName(new Tika(this).detect(file));
    }

    /**
     * Returns the MIME type that best matches the given first few bytes
     * of a document stream. Returns application/octet-stream if no better
     * match is found.
     * <p>
     * The given byte array is expected to be at least {@link #getMinLength()}
     * long, or shorter only if the document stream itself is shorter.
     *
     * @param data first few bytes of a document stream
     * @return matching MIME type
     */
    private MimeType getMimeType(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data is missing");
        } else if (data.length == 0) {
            // See https://issues.apache.org/jira/browse/TIKA-483
            return rootMimeType;
        }

        // Then, check for magic bytes
        MimeType result = null;
        for (Magic magic : magics) {
            if (magic.eval(data)) {
                result = magic.getType();
                break;
            }
        }
 
        if (result != null) {
            // When detecting generic XML (or possibly XHTML),
            // extract the root element and match it against known types
            if ("application/xml".equals(result.getName())
                    || "text/html".equals(result.getName())) {
                XmlRootExtractor extractor = new XmlRootExtractor();

                QName rootElement = extractor.extractRootElement(data);
                if (rootElement != null) {
                    for (MimeType type : xmls) {
                        if (type.matchesXML(
                                rootElement.getNamespaceURI(),
                                rootElement.getLocalPart())) {
                            result = type;
                            break;
                        }
                    }
                } else if ("application/xml".equals(result.getName())) {
                    // Downgrade from application/xml to text/plain since
                    // the document seems not to be well-formed.
                    result = textMimeType;
                }
            }
            return result;
        }

        // Finally, assume plain text if no control bytes are found
        try {
            TextDetector detector = new TextDetector(getMinLength());
            ByteArrayInputStream stream = new ByteArrayInputStream(data);
            return forName(detector.detect(stream, new Metadata()).toString());
        } catch (Exception e) {
            return rootMimeType;
        }
    }

    /**
     * Reads the first {@link #getMinLength()} bytes from the given stream.
     * If the stream is shorter, then the entire content of the stream is
     * returned.
     * <p>
     * The given stream is never {@link InputStream#close() closed},
     * {@link InputStream#mark(int) marked}, or
     * {@link InputStream#reset() reset} by this method.
     *
     * @param stream stream to be read
     * @return first {@link #getMinLength()} (or fewer) bytes of the stream
     * @throws IOException if the stream can not be read
     */
    private byte[] readMagicHeader(InputStream stream) throws IOException {
        if (stream == null) {
            throw new IllegalArgumentException("InputStream is missing");
        }

        byte[] bytes = new byte[getMinLength()];
        int totalRead = 0;

        int lastRead = stream.read(bytes);
        while (lastRead != -1) {
            totalRead += lastRead;
            if (totalRead == bytes.length) {
                return bytes;
            }
            lastRead = stream.read(bytes, totalRead, bytes.length - totalRead);
        }

        byte[] shorter = new byte[totalRead];
        System.arraycopy(bytes, 0, shorter, 0, totalRead);
        return shorter;
    }

    /**
     * Returns the registered media type with the given name (or alias).
     * The named media type is automatically registered (and returned) if
     * it doesn't already exist.
     *
     * @param name media type name (case-insensitive)
     * @return the registered media type with the given name or alias
     * @throws MimeTypeException if the given media type name is invalid
     */
    public MimeType forName(String name) throws MimeTypeException {
        MediaType type = MediaType.parse(name);
        if (type != null) {
            MediaType normalisedType = registry.normalize(type);
            MimeType mime = types.get(normalisedType);
            
            if (mime == null) {
                synchronized (this) {
                   // Double check it didn't already get added while 
                   //  we were waiting for the lock
                   mime = types.get(normalisedType);
                   if (mime == null) {
                      mime = new MimeType(type);
                      add(mime);
                      types.put(type, mime);
                   }
                }
            }
            return mime;
        } else {
            throw new MimeTypeException("Invalid media type name: " + name);
        }
    }

    public synchronized void setSuperType(MimeType type, MediaType parent) {
        registry.addSuperType(type.getType(), parent);
    }

    /**
     * Adds an alias for the given media type. This method should only
     * be called from {@link MimeType#addAlias(String)}.
     *
     * @param type media type
     * @param alias media type alias (normalized to lower case)
     */
    synchronized void addAlias(MimeType type, MediaType alias) {
        registry.addAlias(type.getType(), alias);
    }

    /**
     * Adds a file name pattern for the given media type. Assumes that the
     * pattern being added is <b>not</b> a JDK standard regular expression.
     *
     * @param type
     *            media type
     * @param pattern
     *            file name pattern
     * @throws MimeTypeException
     *             if the pattern conflicts with existing ones
     */
    public void addPattern(MimeType type, String pattern)
            throws MimeTypeException {
        this.addPattern(type, pattern, false);
    }

    /**
     * Adds a file name pattern for the given media type. The caller can specify
     * whether the pattern being added <b>is</b> or <b>is not</b> a JDK standard
     * regular expression via the <code>isRegex</code> parameter. If the value
     * is set to true, then a JDK standard regex is assumed, otherwise the
     * freedesktop glob type is assumed.
     *
     * @param type
     *            media type
     * @param pattern
     *            file name pattern
     * @param isRegex
     *            set to true if JDK std regexs are desired, otherwise set to
     *            false.
     * @throws MimeTypeException
     *             if the pattern conflicts with existing ones.
     *
     */
    public void addPattern(MimeType type, String pattern, boolean isRegex)
            throws MimeTypeException {
        patterns.add(pattern, isRegex, type);
    }

    public MediaTypeRegistry getMediaTypeRegistry() {
        return registry;
    }

    /**
     * Return the minimum length of data to provide to analyzing methods based
     * on the document's content in order to check all the known MimeTypes.
     *
     * @return the minimum length of data to provide.
     * @see #getMimeType(byte[])
     * @see #getMimeType(String, byte[])
     */
    public int getMinLength() {
        // This needs to be reasonably large to be able to correctly detect
        // things like XML root elements after initial comment and DTDs
        return 64 * 1024;
    }

    /**
     * Add the specified mime-type in the repository.
     *
     * @param type
     *            is the mime-type to add.
     */
    void add(MimeType type) {
        registry.addType(type.getType());
        types.put(type.getType(), type);

        // Update the magics index...
        if (type.hasMagic()) {
            magics.addAll(type.getMagics());
        }

        // Update the xml (xmlRoot) index...
        if (type.hasRootXML()) {
            xmls.add(type);
        }
    }

    /**
     * Called after all configured types have been loaded.
     * Initializes the magics and xmls sets.
     */
    void init() {
        for (MimeType type : types.values()) {
            magics.addAll(type.getMagics());
            if (type.hasRootXML()) {
                xmls.add(type);
            }
        }
        Collections.sort(magics);
        Collections.sort(xmls);
    }

    /**
     * Automatically detects the MIME type of a document based on magic
     * markers in the stream prefix and any given metadata hints.
     * <p>
     * The given stream is expected to support marks, so that this method
     * can reset the stream to the position it was in before this method
     * was called.
     *
     * @param input document stream, or <code>null</code>
     * @param metadata metadata hints
     * @return MIME type of the document
     * @throws IOException if the document stream could not be read
     */
    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {
        MediaType type = MediaType.OCTET_STREAM;

        // Get type based on magic prefix
        if (input != null) {
            input.mark(getMinLength());
            try {
                byte[] prefix = readMagicHeader(input);
                type = getMimeType(prefix).getType();
            } finally {
                input.reset();
            }
        }

        // Get type based on resourceName hint (if available)
        String resourceName = metadata.get(Metadata.RESOURCE_NAME_KEY);
        if (resourceName != null) {
            String name = null;

            // Deal with a URI or a path name in as the resource  name
            try {
                URI uri = new URI(resourceName);
                String path = uri.getPath();
                if (path != null) {
                    int slash = path.lastIndexOf('/');
                    if (slash + 1 < path.length()) {
                        name = path.substring(slash + 1);
                    }
                }
            } catch (URISyntaxException e) {
                name = resourceName;
            }

            if (name != null) {
                MediaType hint = getMimeType(name).getType();
                if (registry.isSpecializationOf(hint, type)) {
                    type = hint;
                }
            }
        }

        // Get type based on metadata hint (if available)
        String typeName = metadata.get(Metadata.CONTENT_TYPE);
        if (typeName != null) {
            try {
                MediaType hint = forName(typeName).getType();
                if (registry.isSpecializationOf(hint, type)) {
                    type = hint;
                }
            } catch (MimeTypeException e) {
                // Malformed type name, ignore
            }
        }

        return type;
    }

    private static MimeTypes DEFAULT_TYPES = null;

    /**
     * Get the default MimeTypes. This includes all the build in
     * media types, and any custom override ones present.
     * 
     * @return MimeTypes default type registry
     */
    public static synchronized MimeTypes getDefaultMimeTypes() {
        if (DEFAULT_TYPES == null) {
            try {
                DEFAULT_TYPES = MimeTypesFactory.create(
                        "tika-mimetypes.xml", "custom-mimetypes.xml");
            } catch (MimeTypeException e) {
                throw new RuntimeException(
                        "Unable to parse the default media type registry", e);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Unable to read the default media type registry", e);
            }
        }
        return DEFAULT_TYPES;
    }

}
