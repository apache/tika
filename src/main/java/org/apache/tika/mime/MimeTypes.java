/**
 * Copyright 2007 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;

// Commons Logging imports
import org.apache.commons.logging.Log;

/**
 * This class is a MimeType repository. It gathers a set of MimeTypes and
 * enables to retrieves a content-type from its name, from a file name, or from
 * a magic character sequence.
 * 
 * 
 */
public final class MimeTypes {

    /** The default <code>application/octet-stream</code> MimeType */
    public final static String DEFAULT = "application/octet-stream";

    /** My logger */
    private Log logger = null;

    /** All the registered MimeTypes indexed on their name */
    private Map types = new HashMap();

    /** The patterns matcher */
    private Patterns patterns = new Patterns();

    /** List of all registered magics */
    private ArrayList magics = new ArrayList();

    /** List of all registered rootXML */
    private ArrayList xmls = new ArrayList();

    private Map unsolvedDeps = new HashMap();

    /**
     * A comparator used to sort the mime types based on their magics (it is
     * sorted first on the magic's priority, then on the magic's size).
     */
    final static Comparator MAGICS_COMPARATOR = new Comparator() {
        public int compare(Object o1, Object o2) {
            Magic m1 = (Magic) o1;
            Magic m2 = (Magic) o2;
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
    private final static Comparator LEVELS_COMPARATOR = new Comparator() {
        public int compare(Object o1, Object o2) {
            return ((MimeInfo) o2).getLevel() - ((MimeInfo) o1).getLevel();
        }
    };

    /** The minimum length of data to provide to check all MimeTypes */
    private int minLength = 0;

    /**
     * Creates a new MimeTypes instance.
     * 
     * @param filepath
     *            is the mime-types definitions xml file.
     * @param logger
     *            is it Logger to uses for ouput messages.
     */
    public MimeTypes(String filepath, Log logger) {
        if (logger == null) {
            this.logger = LogFactory.getLog(this.getClass());
        } else {
            this.logger = logger;
        }
        MimeTypesReader reader = new MimeTypesReader(logger);
        add(reader.read(filepath));
    }

    /**
     * Creates a new MimeTypes instance.
     * 
     * @param filepath
     *            is the mime-types definitions xml file.
     * @return A MimeTypes instance for the specified filepath xml file.
     */
    public MimeTypes(String filepath) {
        this(filepath, (Log) null);
    }

    /**
     * Creates a new MimeTypes instance.
     * 
     * @param is
     *            the document of the mime types definition file.
     * @param logger
     *            is it Logger to uses for ouput messages.
     */
    public MimeTypes(Document doc, Log logger) {
        if (logger == null) {
            this.logger = LogFactory.getLog(this.getClass());
        } else {
            this.logger = logger;
        }
        MimeTypesReader reader = new MimeTypesReader(logger);
        add(reader.read(doc));
    }

    /**
     * Creates a new MimeTypes instance.
     * 
     * @param is
     *            the document of the mime types definition file.
     */
    public MimeTypes(Document doc) {
        this(doc, (Log) null);
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
     * 
     * @param name
     *            of the document to analyze.
     * @return the Mime Content Type of the specified document name, or
     *         <code>null</code> if none is found.
     */
    public MimeType getMimeType(String name) {
        MimeType type = patterns.matches(name);
        if (type != null)
            return type;
        // if it's null here, then return the default type
        return forName(DEFAULT);
    }

    /**
     * Find the Mime Content Type of a stream from its content.
     * 
     * @param data
     *            are the first bytes of data of the content to analyze.
     *            Depending on the length of provided data, all known MimeTypes
     *            are checked. If the length of provided data is greater or
     *            egals to the value returned by {@link #getMinLength()}, then
     *            all known MimeTypes are checked, otherwise only the MimeTypes
     *            that could be analyzed with the length of provided data are
     *            analyzed.
     * 
     * @return The Mime Content Type found for the specified data, or
     *         <code>null</code> if none is found.
     * @see #getMinLength()
     */
    public MimeType getMimeType(byte[] data) {
        // Preliminary checks
        if ((data == null) || (data.length < 1)) {
            return null;
        }

        // First, check for XML descriptions (level by level)
        for (int i = 0; i < xmls.size(); i++) {
            MimeType type = ((MimeInfo) xmls.get(i)).getType();
            if (type.matchesXML(data)) {
                return type;
            }
        }

        // Then, check for magic bytes
        for (int i = 0; i < magics.size(); i++) {
            Magic magic = (Magic) magics.get(i);
            if (magic.eval(data)) {
                return magic.getType();
            }
        }
        return null;
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
     * Find a Mime Content Type from its name.
     * 
     * @param name
     *            is the content type name
     * @return the MimeType for the specified name, or <code>null</code> if no
     *         MimeType is registered for this name.
     */
    public MimeType forName(String name) {
        MimeInfo info = (MimeInfo) types.get(name);
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
        types.put(info.getName(), info);

        // Checks for some unsolved dependencies on this new type
        List deps = (List) unsolvedDeps.get(info.getName());
        if (deps != null) {
            int level = info.getLevel();
            for (int i = 0; i < deps.size(); i++) {
                level = Math
                        .max(level, ((MimeInfo) deps.get(i)).getLevel() + 1);
            }
            info.setLevel(level);
            unsolvedDeps.remove(info.getName());
        }

        // Checks if some of my super-types are not already solved
        String[] superTypes = type.getSuperTypes();
        for (int i = 0; i < superTypes.length; i++) {
            MimeInfo superType = (MimeInfo) types.get(superTypes[i]);
            if (superType == null) {
                deps = (List) unsolvedDeps.get(superTypes[i]);
                if (deps == null) {
                    deps = new ArrayList();
                    unsolvedDeps.put(superTypes[i], deps);
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
            Magic[] magics = type.getMagics();
            for (int i = 0; i < magics.length; i++) {
                this.magics.add(magics[i]);
            }
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
        StringBuffer buf = new StringBuffer();
        Iterator iter = types.values().iterator();
        while (iter.hasNext()) {
            MimeType type = ((MimeInfo) iter.next()).getType();
            buf.append(type).append("\n");
        }
        return buf.toString();
    }

    private final class MimeInfo {

        private MimeType type = null;

        private int level = 0;

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
            if (level <= this.level) {
                return;
            }

            this.level = level;
            // Update all my super-types
            String[] supers = type.getSuperTypes();
            for (int i = 0; i < supers.length; i++) {
                MimeInfo sup = (MimeInfo) types.get(supers[i]);
                if (sup != null) {
                    sup.setLevel(level + 1);
                }
            }
        }

        String getName() {
            return type.getName();
        }
    }
}
