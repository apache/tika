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
package org.apache.tika.zip.utils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Resolves the URI references one entry of a zip package makes to another, as an EPUB
 * manifest's hrefs or an OPC package's relationship targets.
 */
public final class ZipEntryPaths {

    private ZipEntryPaths() {
    }

    /**
     * The name of the entry a reference points to: the reference resolved against the entry
     * holding it (RFC 3986, section 5.2) and percent-decoded as a URI path, without the
     * leading slash. A {@code +} stays a {@code +}. A reference that is not a valid URI, as
     * one with a raw space, is taken as a literal path. The entry name it is resolved against
     * is literal: a {@code %}, {@code #} or {@code ?} in it is part of the name.
     *
     * @param from      the name of the entry holding the reference, "" for the package root
     * @param reference the URI reference
     * @return the entry name, or {@code null} for a reference with a scheme or an
     * authority, one without a path (empty or only a fragment), or one that leads above the
     * package root
     */
    public static String resolve(String from, String reference) {
        if (from == null || reference == null) {
            return null;
        }
        try {
            URI ref = uri(reference.trim());
            if (ref.getScheme() != null || ref.getRawAuthority() != null
                    || ref.getRawPath() == null || ref.getRawPath().isEmpty()) {
                return null;
            }
            String base = from.startsWith("/") ? from : "/" + from;
            String path = new URI(null, null, base, null).resolve(ref).normalize().getPath();
            if (path == null || !path.startsWith("/") || path.equals("/..")
                    || path.startsWith("/../")) {
                return null;
            }
            return path.substring(1);
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static URI uri(String s) throws URISyntaxException {
        try {
            return new URI(s);
        } catch (URISyntaxException e) {
            return new URI(null, null, s, null);
        }
    }
}
