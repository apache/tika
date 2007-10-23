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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
public final class MimeTypes {

    /** The default <code>application/octet-stream</code> MimeType */
    public final static String DEFAULT = "application/octet-stream";

    /** All the registered MimeTypes indexed on their name */
    private Map<String, MimeInfo> types = new HashMap<String, MimeInfo>();

    /** The patterns matcher */
    private Patterns patterns = new Patterns();

    /** List of all registered magics */
    private ArrayList<Magic> magics = new ArrayList<Magic>();

    /** List of all registered rootXML */
    private ArrayList<MimeInfo> xmls = new ArrayList<MimeInfo>();

    private Map<String, List<MimeInfo>> unsolvedDeps =
        new HashMap<String, List<MimeInfo>>();

    /**
     * A comparator used to sort the mime types based on their magics (it is
     * sorted first on the magic's priority, then on the magic's size).
     */
    final static Comparator<Magic> MAGICS_COMPARATOR = new Comparator<Magic>() {
        public int compare(Magic m1, Magic m2) {
            int p1 = m1.getPriority();
            int p2 = m2.getPriority();
            if (p1 != p2) {
                return p2 - p1;
            }
            return m2.size() - m1.size();
        }
    };

    /**
     * A comparator used to sort the mime types based on their level (the level
     * is the number of super-types for a type)
     */
    private final static Comparator<MimeInfo> LEVELS_COMPARATOR =
        new Comparator<MimeInfo>() {
            public int compare(MimeInfo o1, MimeInfo o2) {
                return o2.getLevel() - o1.getLevel();
            }
        };

    /** The minimum length of data to provide to check all MimeTypes */
    private int minLength = 0;

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
     * 
     * @param name
     *            of the document to analyze.
     * @return the Mime Content Type of the specified document name, or
     *         <code>null</code> if none is found.
     */
    public MimeType getMimeType(String name) {
        MimeType type = patterns.matches(name.toLowerCase());
        if (type != null)
            return type;
        // if it's null here, then return the default type
        return forName(DEFAULT);
    }

    /**
     * Returns the MIME type that best matches the given first few bytes
     * of a document stream.
     * <p>
     * The given byte array is expected to be at least {@link #getMinLength()}
     * long, or shorter only if the document stream itself is shorter.
     *
     * @param data first few bytes of a document stream
     * @return matching MIME type, or <code>null</code> if no match is found
     */
    public MimeType getMimeType(byte[] data) {
        assert data != null;

        // First, check for XML descriptions (level by level)
        for (MimeInfo info : xmls) {
            MimeType type = info.getType();
            if (type.matchesXML(data)) {
                return type;
            }
        }

        // Then, check for magic bytes
        for (Magic magic : magics) {
            if (magic.eval(data)) {
                return magic.getType();
            }
        }

        return null;
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
        assert stream != null;

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
     * Find a Mime Content Type from its name.
     * 
     * @param name
     *            is the content type name
     * @return the MimeType for the specified name, or <code>null</code> if no
     *         MimeType is registered for this name.
     */
    public MimeType forName(String name) {
        MimeInfo info = types.get(name);
        return (info == null) ? null : info.getType();
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
        return 1024;
        // return minLength;
    }

    /**
     * Add the specified mime-types in the repository.
     * 
     * @param types
     *            are the mime-types to add.
     */
    void add(MimeType[] types) {
        if (types == null) {
            return;
        }
        for (int i = 0; i < types.length; i++) {
            add(types[i]);
        }
    }

    /**
     * Add the specified mime-type in the repository.
     * 
     * @param type
     *            is the mime-type to add.
     */
    void add(MimeType type) {
        if (type == null) {
            return;
        }

        // Add the new type in the repository
        MimeInfo info = new MimeInfo(type);
        types.put(type.getName(), info);

        // Checks for some unsolved dependencies on this new type
        List<MimeInfo> deps = unsolvedDeps.get(type.getName());
        if (deps != null) {
            int level = info.getLevel();
            for (MimeInfo dep : deps) {
                level = Math.max(level, dep.getLevel() + 1);
            }
            info.setLevel(level);
            unsolvedDeps.remove(type.getName());
        }

        for (String name : type.getSuperTypes()) {
            MimeInfo superType = types.get(name);
            if (superType == null) {
                deps = unsolvedDeps.get(name);
                if (deps == null) {
                    deps = new ArrayList<MimeInfo>();
                    unsolvedDeps.put(name, deps);
                }
                deps.add(info);
            }
        }

        // Update minLentgth
        minLength = Math.max(minLength, type.getMinLength());
        // Update the extensions index...
        patterns.add(type.getPatterns(), type);
        // Update the magics index...
        if (type.hasMagic()) {
            magics.addAll(Arrays.asList(type.getMagics()));
        }
        Collections.sort(magics, MAGICS_COMPARATOR);

        // Update the xml (xmlRoot) index...
        if (type.hasRootXML()) {
            this.xmls.add(info);
        }
        Collections.sort(xmls, LEVELS_COMPARATOR);
    }

    // Inherited Javadoc
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (MimeInfo info : types.values()) {
            builder.append(info.getType()).append("\n");
        }
        return builder.toString();
    }

    private final class MimeInfo {

        private final MimeType type;

        private int level;

        MimeInfo(MimeType type) {
            this.type = type;
            this.level = 0;
        }

        MimeType getType() {
            return type;
        }

        int getLevel() {
            return level;
        }

        void setLevel(int level) {
            if (level > this.level) {
                this.level = level;

                // Update all my super-types
                for (String name : type.getSuperTypes()) {
                    MimeInfo info = types.get(name);
                    if (info != null) {
                        info.setLevel(level + 1);
                    }
                }
            }
        }

    }
}
