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
package org.apache.tika.detect;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Content type detection based on the resource name. An instance of this
 * class contains a set of regular expression patterns that are matched
 * against the resource name potentially given as a part of the input metadata.
 * <p>
 * If a pattern matches the given name, then the media type associated with
 * that pattern is returned as the likely content type of the input document.
 * Otherwise the returned type is <code>application/octet-stream</code>.
 * <p>
 * See the {@link #detect(InputStream, Metadata)} method for more details
 * of the matching algorithm.
 *
 * @since Apache Tika 0.3
 */
public class NameDetector implements Detector {

    /**
     * The regular expression patterns used for type detection.
     */
    private final Map<Pattern, MediaType> patterns;

    /**
     * Creates a new content type detector based on the given name patterns.
     * The given pattern map is not copied, so the caller may update the
     * mappings even after this detector instance has been created. However,
     * the map <em>must not be concurrently modified</em> while this instance
     * is used for type detection.
     *
     * @param patterns map from name patterns to corresponding media types
     */
    public NameDetector(Map<Pattern, MediaType> patterns) {
        this.patterns = patterns;
    }

    /**
     * Detects the content type of an input document based on the document
     * name given in the input metadata. The RESOURCE_NAME_KEY attribute of
     * the given input metadata is expected to contain the name (normally
     * a file name or a URL) of the input document.
     * <p>
     * If a resource name is given, then it is first processed as follows.
     * <ol>
     *   <li>
     *     Potential URL query (?...) and fragment identifier (#...)
     *     parts are removed from the end of the resource name.
     *   </li>
     *   <li>
     *     Potential leading path elements (up to the last slash or backslash)
     *     are removed from the beginning of the resource name.
     *   </li>
     *   <li>
     *     Potential URL encodings (%nn, in UTF-8) are decoded.
     *   </li>
     *   <li>
     *     Any leading and trailing whitespace is removed.
     *   </li>
     * </ol>
     * <p>
     * The resulting name string (if any) is then matched in sequence against
     * all the configured name patterns. If a match is found, then the (first)
     * matching media type is returned.
     *
     * @param input ignored
     * @param metadata input metadata, possibly with a RESOURCE_NAME_KEY value
     * @return detected media type, or <code>application/octet-stream</code>
     */
    public MediaType detect(InputStream input, Metadata metadata) {
        // Look for a resource name in the input metadata
        String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
        if (name != null) {
            // If the name is a URL, skip the trailing query and fragment parts
            int question = name.indexOf('?');
            if (question != -1) {
                name = name.substring(0, question);
            }
            int hash = name.indexOf('#');
            if (hash != -1) {
                name = name.substring(0, hash);
            }

            // If the name is a URL or a path, skip all but the last component
            int slash = name.lastIndexOf('/');
            if (slash != -1) {
                name = name.substring(slash + 1);
            }
            int backslash = name.lastIndexOf('\\');
            if (backslash != -1) {
                name = name.substring(backslash + 1);
            }

            // Decode any potential URL encoding
            int percent = name.indexOf('%');
            if (percent != -1) {
                try {
                    name = URLDecoder.decode(name, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalStateException("UTF-8 not supported", e);
                }
            }

            // Skip any leading or trailing whitespace
            name = name.trim();
            if (name.length() > 0) {
                // Match the name against the registered patterns
                for (Pattern pattern : patterns.keySet()) {
                    if (pattern.matcher(name).matches()) {
                        return patterns.get(pattern);
                    }
                }
            }
        }

        return MediaType.OCTET_STREAM;
    }

}
