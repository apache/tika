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

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Internet media type.
 */
public final class MimeType implements Comparable<MimeType>, Serializable {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 4357830439860729201L;

    /**
     * Checks that the given string is a valid Internet media type name
     * based on rules from RFC 2054 section 5.3. For validation purposes the
     * rules can be simplified to the following:
     * <pre>
     * name := token "/" token
     * token := 1*&lt;any (US-ASCII) CHAR except SPACE, CTLs, or tspecials&gt;
     * tspecials :=  "(" / ")" / "&lt;" / "&gt;" / "@" / "," / ";" / ":" /
     *               "\" / <"> / "/" / "[" / "]" / "?" / "="
     * </pre>
     *
     * @param name name string
     * @return <code>true</code> if the string is a valid media type name,
     *         <code>false</code> otherwise
     */
    public static boolean isValid(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name is missing");
        }

        boolean slash = false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch <= ' ' || ch >= 127 || ch == '(' || ch == ')' ||
                    ch == '<' || ch == '>' || ch == '@' || ch == ',' ||
                    ch == ';' || ch == ':' || ch == '\\' || ch == '"' ||
                    ch == '[' || ch == ']' || ch == '?' || ch == '=') {
                return false;
            } else if (ch == '/') {
                if (slash || i == 0 || i + 1 == name.length()) {
                    return false;
                }
                slash = true;
            }
        }
        return slash;
    }

    /**
     * The normalized media type name.
     */
    private final MediaType type;

    /**
     * Description of this media type.
     */
    private String description = "";

    /** The magics associated to this Mime-Type */
    private final ArrayList<Magic> magics = new ArrayList<Magic>();

    /** The root-XML associated to this Mime-Type */
    private final ArrayList<RootXML> rootXML = new ArrayList<RootXML>();

    /** The minimum length of data to provides for magic analyzis */
    private int minLength = 0;

    /**
     * Creates a media type with the give name and containing media type
     * registry. The name is expected to be valid and normalized to lower
     * case. This constructor should only be called by
     * {@link MimeTypes#forName(String)} to keep the media type registry
     * up to date.
     *
     * @param type normalized media type name
     */
    MimeType(MediaType type) {
        if (type == null) {
            throw new IllegalArgumentException("Media type name is missing");
        }
        this.type = type;
    }

    /**
     * Returns the normalized media type name.
     *
     * @return media type
     */
    public MediaType getType() {
        return type;
    }

    /**
     * Returns the name of this media type.
     *
     * @return media type name (lower case)
     */
    public String getName() {
        return type.toString();
    }

    /**
     * Returns the description of this media type.
     *
     * @return media type description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the description of this media type.
     *
     * @param description media type description
     */
    public void setDescription(String description) {
        if (description == null) {
            throw new IllegalArgumentException("Description is missing");
        }
        this.description = description;
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

    boolean matchesXML(String namespaceURI, String localName) {
        for (RootXML xml : rootXML) {
            if (xml.matches(namespaceURI, localName)) {
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
        return matchesMagic(data);
    }

    /**
     * Defines a RootXML description. RootXML is made of a localName and/or a
     * namespaceURI.
     */
    class RootXML implements Serializable {

        /**
         * Serial version UID.
         */
        private static final long serialVersionUID = 5140496601491000730L;

        private MimeType type = null;

        private String namespaceURI = null;

        private String localName = null;

        RootXML(MimeType type, String namespaceURI, String localName) {
            if (isEmpty(namespaceURI) && isEmpty(localName)) {
                throw new IllegalArgumentException(
                        "Both namespaceURI and localName cannot be empty");
            }
            this.type = type;
            this.namespaceURI = namespaceURI;
            this.localName = localName;
        }

        boolean matches(String namespaceURI, String localName) {
            //Compare namespaces
            if (!isEmpty(this.namespaceURI)) {
                if (!this.namespaceURI.equals(namespaceURI)) {
                    return false;
                }
            }
            else{
                // else if it was empty then check to see if the provided namespaceURI
                // is empty. If it is not, then these two aren't equal and return false
                if(!isEmpty(namespaceURI)){
                    return false;
                }
            }

            //Compare root element's local name
            if (!isEmpty(this.localName)) {
                if (!this.localName.equals(localName)) {
                    return false;
                }
            }
            else{
                // else if it was empty then check to see if the provided localName
                // is empty. If it is not, then these two aren't equal and return false 
                if(!isEmpty(localName)){
                    return false;
                }
            }
            return true;
        }

        /**
         * Checks if a string is null or empty.
         */
        private boolean isEmpty(String str) {
            return (str == null) || (str.equals(""));
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
            return type + ", " + namespaceURI + ", " + localName;
        }
    }

    //----------------------------------------------------------< Comparable >

    public int compareTo(MimeType mime) {
        return type.compareTo(mime.type);
    }

    //--------------------------------------------------------------< Object >

    /**
     * Returns the name of this media type.
     *
     * @return media type name
     */
    public String toString() {
        return type.toString();
    }

}
