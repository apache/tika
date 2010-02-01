/**
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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.xml.namespace.QName;

import org.apache.tika.detect.Detector;
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
public final class MimeTypes implements Detector {

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
     * Lookup table for all the ASCII/ISO-Latin/UTF-8/etc. control bytes
     * in the range below 0x20 (the space character). If an entry in this
     * table is <code>true</code> then that byte is very unlikely to occur
     * in a plain text document.
     * <p>
     * The contents of this lookup table are based on the following definition
     * from section 4 of the "Content-Type Processing Model" Internet-draft
     * (<a href="http://webblaze.cs.berkeley.edu/2009/mime-sniff/mime-sniff.txt"
     * >draft-abarth-mime-sniff-01</a>).
     * <pre>
     * +-------------------------+
     * | Binary data byte ranges |
     * +-------------------------+
     * | 0x00 -- 0x08            |
     * | 0x0B                    |
     * | 0x0E -- 0x1A            |
     * | 0x1C -- 0x1F            |
     * +-------------------------+
     * </pre>
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-154">TIKA-154</a>
     */
    private static final boolean[] IS_CONTROL_BYTE = new boolean[0x20];
    static {
        Arrays.fill(IS_CONTROL_BYTE, true);
        IS_CONTROL_BYTE[0x09] = false; // tabulator
        IS_CONTROL_BYTE[0x0A] = false; // new line
        IS_CONTROL_BYTE[0x0C] = false; // new page
        IS_CONTROL_BYTE[0x0D] = false; // carriage return
        IS_CONTROL_BYTE[0x1B] = false; // escape
    }

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
    
    /** All the registered MimeTypes indexed on their name */
    private final Map<String, MimeType> types = new HashMap<String, MimeType>();

    /** The patterns matcher */
    private Patterns patterns = new Patterns();

    /** List of all registered magics */
    private SortedSet<Magic> magics = new TreeSet<Magic>();

    /** List of all registered rootXML */
    private SortedSet<MimeType> xmls = new TreeSet<MimeType>();

    private final XmlRootExtractor xmlRootExtractor;

    public MimeTypes() {
        rootMimeType = new MimeType(this, OCTET_STREAM);
        textMimeType = new MimeType(this, PLAIN_TEXT);
        xmlMimeType = new MimeType(this, XML);
        
        try {
            textMimeType.setSuperType(rootMimeType);
            xmlMimeType.setSuperType(rootMimeType);
        } catch (MimeTypeException e) {
            throw new IllegalStateException("Error in MimeType logic", e);
        }

        types.put(rootMimeType.getName(), rootMimeType);
        types.put(textMimeType.getName(), textMimeType);
        types.put(xmlMimeType.getName(), xmlMimeType);

        try {
            xmlRootExtractor = new XmlRootExtractor();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to create a XmlRootExtractor", e);
        }
    }

    /**
     * Find the Mime Content Type of a file.
     *
     * @param file
     *            to analyze.
     * @return the Mime Content Type of the specified file, or <code>null</code>
     *         if none is found.
     */
    public MimeType getMimeType(File file) {
        return getMimeType(file.getName());
    }

    /**
     * Find the Mime Content Type of a document from its URL.
     *
     * @param url
     *            of the document to analyze.
     * @return the Mime Content Type of the specified document URL, or
     *         <code>null</code> if none is found.
     */
    public MimeType getMimeType(URL url) {
        return getMimeType(url.getPath());
    }

    /**
     * Find the Mime Content Type of a document from its name.
     * Returns application/octet-stream if no better match is found.
     *
     * @param name of the document to analyze.
     * @return the Mime Content Type of the specified document name
     */
    public MimeType getMimeType(String name) {
        MimeType type = patterns.matches(name);
        if (type != null) {
            return type;
        }
        type = patterns.matches(name.toLowerCase());
        if (type != null) {
            return type;
        } else {
            return rootMimeType;
        }
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
    public MimeType getMimeType(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data is missing");
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
                QName rootElement = xmlRootExtractor.extractRootElement(data);
                if (rootElement != null) {
                    for (MimeType type : xmls) {
                        if (type.matchesXML(
                                rootElement.getNamespaceURI(),
                                rootElement.getLocalPart())) {
                            result = type;
                            break;
                        }
                    }
                }
            }
            return result;
        }


        // Finally, assume plain text if no control bytes are found
        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xFF; // prevent sign extension
            if (b < IS_CONTROL_BYTE.length && IS_CONTROL_BYTE[b]) {
                return rootMimeType;
            }
        }
        return textMimeType;
    }

    /**
     * Returns the MIME type that best matches the first few bytes of the
     * given document stream.
     *
     * @see #getMimeType(byte[])
     * @param stream document stream
     * @return matching MIME type, or <code>null</code> if no match is found
     * @throws IOException if the stream can be read
     */
    public MimeType getMimeType(InputStream stream) throws IOException {
        return getMimeType(readMagicHeader(stream));
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

    public String getType(String typeName, String url, byte[] data) {
        try {
            Metadata metadata = new Metadata();
            if (url != null) {
                metadata.set(Metadata.RESOURCE_NAME_KEY, url);
            }
            if (typeName != null) {
                metadata.set(Metadata.CONTENT_TYPE, typeName);
            }
            return detect(new ByteArrayInputStream(data), metadata).toString();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "ByteArrayInputStream throws an IOException!", e);
        }
    }

    /**
     * Determines the MIME type of the resource pointed to by the specified URL.
     * Examines the file's header, and if it cannot determine the MIME type
     * from the header, guesses the MIME type from the URL extension
     * (e.g. "pdf).
     *
     * @param url URL of the document
     * @return type of the document
     * @throws IOException if the document can not be accessed
     */
    public String getType(URL url) throws IOException {
        InputStream stream = url.openStream();
        try {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, url.toString());
            return detect(stream, metadata).toString();
        } finally {
            stream.close();
        }
    }

    /**
     * Find the Mime Content Type of a document from its name and its content.
     * The policy used to guess the Mime Content Type is:
     * <ol>
     * <li>Try to find the type based on the provided data.</li>
     * <li>If a type is found, then return it, otherwise try to find the type
     * based on the file name</li>
     * </ol>
     *
     * @param name
     *            of the document to analyze.
     * @param data
     *            are the first bytes of the document's content.
     * @return the Mime Content Type of the specified document, or
     *         <code>null</code> if none is found.
     * @see #getMinLength()
     */
    public MimeType getMimeType(String name, byte[] data) {
        // First, try to get the mime-type from the content
        MimeType mimeType = getMimeType(data);

        // If no mime-type found, then try to get the mime-type from
        // the document name
        if (mimeType == null) {
            mimeType = getMimeType(name);
        }

        return mimeType;
    }

    /**
     * Returns the MIME type that best matches the given document name and
     * the first few bytes of the given document stream.
     *
     * @see #getMimeType(String, byte[])
     * @param name document name
     * @param stream document stream
     * @return matching MIME type, or <code>null</code> if no match is found
     * @throws IOException if the stream can not be read
     */
    public MimeType getMimeType(String name, InputStream stream)
            throws IOException {
        return getMimeType(name, readMagicHeader(stream));
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
    public synchronized MimeType forName(String name)
            throws MimeTypeException {
        if (MimeType.isValid(name)) {
            name = name.toLowerCase();
            MimeType type = types.get(name);
            if (type == null) {
                type = new MimeType(this, name);
                if (name.startsWith("text/")) {
                    type.setSuperType(textMimeType);
                } else if (name.endsWith("+xml")) {
                	type.setSuperType(xmlMimeType);
                } else {
                    type.setSuperType(rootMimeType);
                }
                types.put(name, type);
            }
            return type;
        } else {
            throw new MimeTypeException("Invalid media type name: " + name);
        }
    }

    /**
     * Adds an alias for the given media type. This method should only
     * be called from {@link MimeType#addAlias(String)}.
     *
     * @param type media type
     * @param alias media type alias (normalized to lower case)
     * @throws MimeTypeException if the alias already exists
     */
    synchronized void addAlias(MimeType type, String alias)
            throws MimeTypeException {
        if (!types.containsKey(alias)) {
            types.put(alias, type);
        } else {
            throw new MimeTypeException(
                    "Media type alias already exists: " + alias);
        }
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
        return 8 * 1024;
    }

    /**
     * Add the specified mime-type in the repository.
     *
     * @param type
     *            is the mime-type to add.
     */
    void add(MimeType type) {
        // Update the magics index...
        if (type.hasMagic()) {
            magics.addAll(Arrays.asList(type.getMagics()));
        }

        // Update the xml (xmlRoot) index...
        if (type.hasRootXML()) {
            xmls.add(type);
        }
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
        MimeType type = rootMimeType;

        // Get type based on magic prefix
        if (input != null) {
            input.mark(getMinLength());
            try {
                byte[] prefix = readMagicHeader(input);
                type = getMimeType(prefix);
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
                MimeType hint = getMimeType(name);
                if (hint.isDescendantOf(type)) {
                    type = hint;
                }
            }
        }

        // Get type based on metadata hint (if available)
        String typeName = metadata.get(Metadata.CONTENT_TYPE);
        if (typeName != null) {
            try {
                MimeType hint = forName(typeName);
                if (hint.isDescendantOf(type)) {
                    type = hint;
                }
            } catch (MimeTypeException e) {
                // Malformed type name, ignore
            }
        }

        return MediaType.parse(type.getName());
    }

}
