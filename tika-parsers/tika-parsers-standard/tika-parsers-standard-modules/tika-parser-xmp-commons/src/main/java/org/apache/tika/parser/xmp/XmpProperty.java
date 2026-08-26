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
package org.apache.tika.parser.xmp;

/** One flattened XMP leaf: namespace URI, xmpcore-style path (prefix:name with [i] indices), value, xml:lang. */
public final class XmpProperty {

    public final String namespaceURI;
    public final String path;
    public final String value;
    public final String lang;   // xml:lang qualifier of a lang-alt item, or null

    public XmpProperty(String namespaceURI, String path, String value) {
        this(namespaceURI, path, value, null);
    }

    public XmpProperty(String namespaceURI, String path, String value, String lang) {
        this.namespaceURI = namespaceURI == null ? "" : namespaceURI;
        this.path = path;
        this.value = value;
        this.lang = lang;
    }

    /** Local name of the leaf, e.g. {@code dc:creator[1]} -&gt; {@code creator}. */
    public String localName() {
        int slash = path.lastIndexOf('/');
        String last = slash < 0 ? path : path.substring(slash + 1);
        last = last.replaceAll("\\[\\d+\\]$", "");   // drop trailing array index
        int colon = last.lastIndexOf(':');
        return colon < 0 ? last : last.substring(colon + 1);
    }

    @Override
    public String toString() {
        return path + "=" + value;
    }
}
