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
package org.apache.tika.io;

import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.utils.StringUtils;


public class FilenameUtils {

    private static final MimeTypes MIME_TYPES = MimeTypes.getDefaultMimeTypes();
    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("[A-Za-z0-9]{1,10}://+");
    /**
     * Reserved characters
     */
    public final static char[] RESERVED_FILENAME_CHARACTERS =
            {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D,
                    0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A,
                    0x1B, 0x1C, 0x1D, 0x1E, 0x1F, '?', ':', '*', '<', '>', '|', '"', '\''};

    private final static HashSet<Character> RESERVED = new HashSet<>(38);


    static {
        for (char reservedFilenameCharacter : RESERVED_FILENAME_CHARACTERS) {
            RESERVED.add(reservedFilenameCharacter);
        }
    }

    private final static Pattern ASCII_NUMERIC = Pattern.compile("\\A\\.(?i)[a-z0-9]{1,5}\\Z");

    /**
     * Scans the given file name for reserved characters on different OSs and
     * file systems and returns a sanitized version of the name with the
     * reserved chars replaced by their hexadecimal value.
     * <p>
     * For example <code>why?.zip</code> will be converted into <code>why%3F.zip</code>
     *
     * @param name the file name to be normalized - NOT NULL
     * @return the normalized file name
     * @throws IllegalArgumentException if name is null
     */
    public static String normalize(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }

        StringBuilder sb = new StringBuilder();

        for (char c : name.toCharArray()) {
            if (RESERVED.contains(c)) {
                sb.append('%').append((c < 16) ? "0" : "")
                        .append(Integer.toHexString(c).toUpperCase(Locale.ROOT));
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * This is a duplication of the algorithm and functionality
     * available in commons io FilenameUtils.  If Java's File were
     * able handle Windows file paths correctly in linux,
     * we wouldn't need this.
     * <p>
     * The goal of this is to get a filename from a path.
     * The package parsers and some other embedded doc
     * extractors could put anything into TikaCoreProperties.RESOURCE_NAME_KEY.
     * <p>
     * If a careless client used that filename as if it were a
     * filename and not a path when writing embedded files,
     * bad things could happen.  Consider: "../../../my_ppt.ppt".
     * <p>
     * Consider using this in combination with {@link #normalize(String)}.
     *
     * @param path path to strip
     * @return empty string or a filename, never null
     */
    public static String getName(final String path) {

        if (path == null || path.isEmpty()) {
            return StringUtils.EMPTY;
        }
        int unix = path.lastIndexOf("/");
        int windows = path.lastIndexOf("\\");
        //some macintosh file names are stored with : as the delimiter
        //also necessary to properly handle C:somefilename
        int colon = path.lastIndexOf(":");
        String cand = path.substring(Math.max(colon, Math.max(unix, windows)) + 1);
        if (cand.equals("..") || cand.equals(".")) {
            return StringUtils.EMPTY;
        }
        return cand;
    }

    /**
     * This includes the period, e.g. ".pdf".
     * This requires that an extension contain only ascii alphanumerics
     * and it requires that an extension length be 5 or less.
     * @param path
     * @return the suffix or an empty string if one could not be found
     */
    public static String getSuffixFromPath(String path) {
        String n = getName(path);
        int i = n.lastIndexOf(".");
        //arbitrarily sets max extension length
        if (i > -1 && n.length() - i < 6) {
            String suffix = n.substring(i);
            if (ASCII_NUMERIC.matcher(suffix).matches()) {
                return suffix;
            }
        }
        return StringUtils.EMPTY;
    }

    public static String getSanitizedEmbeddedFileName(Metadata metadata,
                                                      String defaultExtension, int maxLength) {
        String path = getEmbeddedName(metadata);
        //fName could be a full path or null
        if (StringUtils.isBlank(path)) {
            return null;
        }
        path = path.replaceAll("\u0000", " ");
        if (path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }

        int prefixLength = getPrefixLength(path);
        if (prefixLength > 0) {
            path = path.substring(prefixLength);
        }
        path = path.replaceAll("[:\\\\]+", "/");
        String fName = getName(path);
        fName = normalize(fName);
        String extension = FilenameUtils.getSuffixFromPath(fName);
        if (extension.equals(fName)) {
            return null;
        }
        String namePart = null;
        if (StringUtils.isBlank(extension)) {
            namePart = fName;
            extension = calculateExtension(metadata, defaultExtension);
        } else {
            namePart = fName.substring(0, fName.length() - extension.length());
        }
        if (StringUtils.isBlank(namePart)) {
            return null;
        }
        //remove all initial .
        namePart = namePart.replaceAll("\\A\\.+", "_");
        //defense in depth. We shouldn't need this
        namePart = namePart.replaceAll("(\\.\\.)+", "_");
        namePart = namePart.replaceAll("[/\\\\]+", "_");
        namePart = namePart.replaceAll(":+", "_");
        namePart = namePart.trim();

        if (StringUtils.isBlank(namePart)) {
            return null;
        }

        //if path is > max length, return only the name part
        if (namePart.length() > maxLength) {
            return namePart.substring(0, maxLength - extension.length() - 3) + "..." + extension;
        }
        return namePart + extension;

    }

    /**
     * This tries to sanitize dangerous user generated embedded file paths.
     * If trusting these paths for writing files, users should run checks to make
     * sure that the generated file path does not zipslip out of the target directory.
     *
     * @param metadata
     * @param defaultExtension
     * @param maxLength
     * @return
     */
    public static String getSanitizedEmbeddedFilePath(Metadata metadata,
                                                      String defaultExtension, int maxLength) {
        String path = getEmbeddedPath(metadata);
        //fName could be a full path or null
        if (StringUtils.isBlank(path)) {
            return null;
        }
        path = path.replaceAll("\u0000", " ");
        int prefixLength = getPrefixLength(path);
        if (prefixLength > 0) {
            path = path.substring(prefixLength);
        }
        path = path.replaceAll("\\\\", "/");
        path = removeProtocol(path);
        path = path.replaceAll(":+", "/");
        path = path.replaceAll("/+", "/");
        path = normalize(path);
        path = path.replaceAll("\\.{2,}", ".");
        path = path.replaceAll("\\./", "/");
        if (path.isBlank()) {
            return null;
        }
        path = path.replaceAll("\\A/+", "");
        path = path.replaceAll("/+\\Z", "");
        String fName = getName(path);
        if (StringUtils.isBlank(fName)) {
            return null;
        }
        String relPath = "";
        if (path.length() > fName.length()) {
            relPath = path.substring(0, path.length() - fName.length() - 1);
        }
        String extension = FilenameUtils.getSuffixFromPath(fName);
        if (extension.equals(path)) {
            return extension;
        }
        String namePart = null;
        if (StringUtils.isBlank(extension)) {
            namePart = path;
            extension = calculateExtension(metadata, defaultExtension);
        } else {
            namePart = fName.substring(0, fName.length() - extension.length());
        }
        if (StringUtils.isBlank(namePart)) {
            return null;
        }
        //remove all initial .
        namePart = namePart.replaceAll("\\A\\.+", "_");
        //defense in depth. We shouldn't need this
        namePart = namePart.replaceAll("\\.{2,}", ".");
        namePart = namePart.replaceAll("[/\\\\]+", "_");

        if (StringUtils.isBlank(namePart)) {
            return null;
        }
        String retPath = StringUtils.isBlank(relPath) ? namePart + extension : relPath + "/" + namePart + extension;

        //if path is > max length, return only the name part
        if (retPath.length() > maxLength) {
            if (namePart.length() > maxLength) {
                return namePart.substring(0, maxLength - extension.length() - 3) + "..." + extension;
            }
            return namePart + extension;
        }
        return retPath;
    }

    private static int getPrefixLength(String path) {
        int prefixLength = org.apache.commons.io.FilenameUtils.getPrefixLength(path);
        if (prefixLength > 0) {
            return prefixLength;
        }
        if (path.length() == 2 && path.charAt(0) >= 'A' && path.charAt(0) <= 'Z' && path.charAt(1) == ':') {
            return 2;
        }
        return 0;
    }

    private static String removeProtocol(String path) {
        Matcher m = PROTOCOL_PATTERN.matcher(path);
        int last = -1;
        while (m.find()) {
            last = m.end();
        }
        if (last > -1) {
            return path.substring(last);
        }
        return path;
    }

    //may return null
    private static String getEmbeddedPath(Metadata metadata) {
        String path = metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
        if (! StringUtils.isBlank(path)) {
            return path;
        }
        path = metadata.get(TikaCoreProperties.INTERNAL_PATH);
        if (! StringUtils.isBlank(path)) {
            return path;
        }
        path = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (! StringUtils.isBlank(path)) {
            return path;
        }
        path = metadata.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID);
        if (! StringUtils.isBlank(path)) {
            return path;
        }
        return metadata.get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME);
    }

    //this tries for resource name first, and then backs off to path
    private static String getEmbeddedName(Metadata metadata) {
        String path = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (! StringUtils.isBlank(path)) {
            return path;
        }
        path = metadata.get(TikaCoreProperties.INTERNAL_PATH);
        if (! StringUtils.isBlank(path)) {
            return path;
        }
        path = metadata.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID);
        if (! StringUtils.isBlank(path)) {
            return path;
        }
        path = metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
        if (! StringUtils.isBlank(path)) {
            return path;
        }
        return metadata.get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME);
    }

    /**
     * Calculate the extension based on the {@link Metadata#CONTENT_TYPE} value.
     * On parse exception or null value, return the default value.
     *
     * @param metadata
     * @param defaultValue
     * @return the extension based on the mime type, including the initial "."
     */
    public static String calculateExtension(Metadata metadata, String defaultValue) {
        String mime = metadata.get(Metadata.CONTENT_TYPE);
        if (mime == null) {
            return defaultValue;
        }
        String ext = lookupExtension(mime);
        if (ext != null) {
            return ext;
        }
        // Handle OCR media types (e.g., image/ocr-jpeg -> image/jpeg)
        // These are internal routing types that don't have registered extensions
        if (mime.startsWith("image/ocr-")) {
            String normalized = "image/" + mime.substring("image/ocr-".length());
            ext = lookupExtension(normalized);
            if (ext != null) {
                return ext;
            }
        }
        return ".bin";
    }

    private static String lookupExtension(String mime) {
        try {
            String ext = MIME_TYPES
                    .forName(mime)
                    .getExtension();
            if (!StringUtils.isBlank(ext)) {
                return ext;
            }
        } catch (MimeTypeException e) {
            //swallow
        }
        return null;
    }

}
