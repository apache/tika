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
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Pattern;

import org.apache.tika.utils.StringUtil;

/**
 * Defines a Mime Content Type.
 * 
 * 
 */
public final class MimeType implements Comparable<MimeType> {

    /** The primary and sub types separator */
    private final static String SEPARATOR = "/";

    /** The parameters separator */
    private final static String PARAMS_SEP = ";";

    /** Special characters not allowed in content types. */
    private final static String SPECIALS = "()<>@,;:\\\"/[]?=";

    /** The Mime-Type full name */
    private String name = null;

    /** The Mime-Type primary type */
    private String primary = null;

    /** The Mime-Type sub type */
    private String sub = null;

    /** The Mime-Type description */
    private String description = null;

    /** The Mime-Type associated recognition patterns */
    private Patterns patterns = null;

    /** The magics associated to this Mime-Type */
    private ArrayList<Magic> magics = null;

    /** The aliases Mime-Types for this one */
    private ArrayList<String> aliases = null;

    /** The root-XML associated to this Mime-Type */
    private ArrayList<RootXML> rootXML = null;

    /** The sub-class-of associated to this Mime-Type */
    private ArrayList<String> superTypes = null;

    /** The mime-type level (regarding its subTypes) */
    private int level = 0;

    /** The minimum length of data to provides for magic analyzis */
    private int minLength = 0;

    /**
     * Creates a MimeType from a String.
     * 
     * @param name
     *            the MIME content type String.
     */
    public MimeType(String name) throws MimeTypeException {

        if (name == null || name.length() <= 0) {
            throw new MimeTypeException("The type can not be null or empty");
        }

        // Split the two parts of the Mime Content Type
        String[] parts = name.split(SEPARATOR, 2);

        // Checks validity of the parts
        if (parts.length != 2) {
            throw new MimeTypeException("Invalid Content Type " + name);
        }
        init(parts[0], parts[1]);
    }

    /**
     * Creates a MimeType with the given primary type and sub type.
     * 
     * @param primary
     *            the content type primary type.
     * @param sub
     *            the content type sub type.
     */
    public MimeType(String primary, String sub) throws MimeTypeException {
        init(primary, sub);
    }

    /** Init method used by constructors. */
    private void init(String primary, String sub) throws MimeTypeException {

        // Preliminary checks...
        if ((primary == null) || (primary.length() <= 0) || (!isValid(primary))) {
            throw new MimeTypeException("Invalid Primary Type " + primary);
        }
        // Remove optional parameters from the sub type
        String clearedSub = null;
        if (sub != null) {
            clearedSub = sub.split(PARAMS_SEP)[0];
        }
        if ((clearedSub == null) || (clearedSub.length() <= 0)
                || (!isValid(clearedSub))) {
            throw new MimeTypeException("Invalid Sub Type " + clearedSub);
        }

        // All is ok, assign values
        this.primary = primary.toLowerCase().trim();
        this.sub = clearedSub.toLowerCase().trim();
        this.name = this.primary + SEPARATOR + this.sub;
        this.patterns = new Patterns();
        this.magics = new ArrayList<Magic>();
        this.aliases = new ArrayList<String>();
        this.rootXML = new ArrayList<RootXML>();
        this.superTypes = new ArrayList<String>();
    }

    /**
     * Cleans a content-type. This method cleans a content-type by removing its
     * optional parameters and returning only its
     * <code>primary-type/sub-type</code>.
     * 
     * @param type
     *            is the content-type to clean.
     * @return the cleaned version of the specified content-type.
     * @throws MimeTypeException
     *             if something wrong occurs during the parsing/cleaning of the
     *             specified type.
     */
    public final static String clean(String type) throws MimeTypeException {
        return (new MimeType(type)).getName();
    }

    /**
     * Return the name of this mime-type.
     * 
     * @return the name of this mime-type.
     */
    public String getName() {
        return name;
    }

    /**
     * Return the primary type of this mime-type.
     * 
     * @return the primary type of this mime-type.
     */
    public String getPrimaryType() {
        return primary;
    }

    /**
     * Return the sub type of this mime-type.
     * 
     * @return the sub type of this mime-type.
     */
    public String getSubType() {
        return sub;
    }

    // Inherited Javadoc
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append(name).append(" -- ").append(getDescription()).append("\n")
                .append("Aliases: ");
        if (aliases.size() < 1) {
            buf.append(" NONE");
        }
        buf.append("\n");
        for (int i = 0; i < aliases.size(); i++) {
            buf.append("\t").append(aliases.get(i)).append("\n");
        }
        buf.append("Patterns:");
        String[] patterns = this.patterns.getPatterns();
        if (patterns.length < 1) {
            buf.append(" NONE");
        }
        buf.append("\n");
        for (int i = 0; i < patterns.length; i++) {
            buf.append("\t").append(patterns[i]).append("\n");
        }
        buf.append("Magics:  ");
        if (magics.size() < 1) {
            buf.append(" NONE");
        }
        buf.append("\n");
        for (int i = 0; i < magics.size(); i++) {
            buf.append("\t").append(magics.get(i)).append("\n");
        }

        return buf.toString();
    }

    /**
     * Indicates if an object is equal to this mime-type. The specified object
     * is equal to this mime-type if it is not null, and it is an instance of
     * MimeType and its name is equals to this mime-type.
     * 
     * @param object
     *            the reference object with which to compare.
     * @return <code>true</code> if this mime-type is equal to the object
     *         argument; <code>false</code> otherwise.
     */
    public boolean equals(Object object) {
        try {
            return ((MimeType) object).getName().equals(this.name);
        } catch (Exception e) {
            return false;
        }
    }

    // Inherited Javadoc
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Return the description of this mime-type.
     * 
     * @return the description of this mime-type.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the description of this mime-type.
     * 
     * @param description
     *            the description of this mime-type.
     */
    void setDescription(String description) {
        this.description = description;
    }

    /**
     * Add a supported file-naming pattern.
     * 
     * @param pattern
     *            to add to the list of recognition pattern for this mime-type.
     */
    void addPattern(String pattern) {
        patterns.add(pattern, this);
    }

    /**
     * Return the recogition patterns for this mime-type
     * 
     * @return the recoginition patterns associated to this mime-type.
     */
    String[] getPatterns() {
        return patterns.getPatterns();
    }

    /**
     * Add an alias to this mime-type
     * 
     * @param alias
     *            to add to this mime-type.
     */
    void addAlias(String alias) {
        aliases.add(alias);
    }

    /**
     * Add some rootXML info to this mime-type
     * 
     * @param namespaceURI
     * @param localName
     */
    void addRootXML(String namespaceURI, String localName) {
        rootXML.add(new RootXML(this, namespaceURI, localName));
    }

    boolean matchesXML(byte[] data) {
        RootXML xml = null;
        String content = new String(data);
        for (int i = 0; i < rootXML.size(); i++) {
            xml = rootXML.get(i);
            if (xml.matches(content)) {
                return true;
            }
        }
        return false;
    }

    boolean hasRootXML() {
        return (rootXML.size() > 0);
    }

    RootXML[] getRootXMLs() {
        return rootXML.toArray(new RootXML[rootXML.size()]);
    }

    void addSuperType(String type) {
        superTypes.add(type);
    }

    boolean hasSuperType() {
        return (superTypes.size() > 0);
    }

    /**
     * Returns the super types of this mime-type. A type is a super type of
     * another type if any instance of the second type is also an instance of
     * the first.
     */
    public String[] getSuperTypes() {
        return superTypes.toArray(new String[superTypes.size()]);
    }

    int getLevel() {
        return level;
    }

    void setLevel(int level) {
        this.level = level;
    }

    /**
     * Return the recogition patterns for this mime-type
     * 
     * @return the recoginition patterns associated to this mime-type.
     */
    public String[] getAliases() {
        return aliases.toArray(new String[aliases.size()]);
    }

    Magic[] getMagics() {
        return magics.toArray(new Magic[magics.size()]);
    }

    void addMagic(Magic magic) {
        if (magic == null) {
            return;
        }
        magics.add(magic);
    }

    int getMinLength() {
        return minLength;
    }

    public boolean hasMagic() {
        return (magics.size() > 0);
    }

    public boolean matches(String url) {
        return (patterns.matches(url) == this);
    }

    public boolean matchesMagic(byte[] data) {
        for (int i = 0; i < magics.size(); i++) {
            Magic magic = magics.get(i);
            if (magic.eval(data)) {
                return true;
            }
        }
        return false;
    }

    public boolean matches(byte[] data) {
        return matchesXML(data) || matchesMagic(data);
    }

    /** Checks if the specified primary or sub type is valid. */
    private boolean isValid(String type) {
        return (type != null) && (type.trim().length() > 0)
                && !hasCtrlOrSpecials(type);
    }

    /** Checks if the specified string contains some special characters. */
    private boolean hasCtrlOrSpecials(String type) {
        int len = type.length();
        int i = 0;
        while (i < len) {
            char c = type.charAt(i);
            if (c <= '\032' || SPECIALS.indexOf(c) > 0) {
                return true;
            }
            i++;
        }
        return false;
    }

    /**
     * Defines a RootXML description. RootXML is made of a localName and/or a
     * namespaceURI.
     */
    class RootXML {

        private final static int PATTERN_FLAGS = Pattern.CASE_INSENSITIVE
                | Pattern.DOTALL | Pattern.MULTILINE;

        private MimeType type = null;

        private String namespaceURI = null;

        private String localName = null;

        private Pattern pattern = null;

        RootXML(MimeType type, String namespaceURI, String localName) {
            this.type = type;
            this.namespaceURI = namespaceURI;
            this.localName = localName;
            if ((StringUtil.isEmpty(namespaceURI))
                    && (StringUtil.isEmpty(localName))) {
                throw new IllegalArgumentException(
                        "Both namespaceURI and localName cannot be null");
            }
            String regex = null;
            if (StringUtil.isEmpty(namespaceURI)) {
                regex = ".*<" + localName + "[^<>]*>.*";
            } else if (StringUtil.isEmpty(localName)) {
                regex = ".*<[^<>]*\\p{Space}xmlns=[\"\']?" + namespaceURI
                        + "[\"\']?[^<>]*>.*";
            } else {
                regex = ".*<" + localName + "[^<>]*\\p{Space}xmlns=[\"\']?"
                        + namespaceURI + "[\"\']?[^<>]*>.*";
            }
            this.pattern = Pattern.compile(regex, PATTERN_FLAGS);
        }

        boolean matches(byte[] data) {
            return matches(new String(data));
        }

        boolean matches(String data) {
            return pattern.matcher(data).matches();
        }

        MimeType getType() {
            return type;
        }

        String getNameSpaceURI() {
            return namespaceURI;
        }

        String getLocalName() {
            return localName;
        }

        public String toString() {
            return new StringBuffer().append(type.getName()).append(", ")
                    .append(namespaceURI).append(", ").append(localName)
                    .toString();
        }
    }

    public int compareTo(MimeType o) {
        int diff = level - o.level;
        if (diff == 0) {
            diff = name.compareTo(o.name);
        }
        return diff;
    }

}
