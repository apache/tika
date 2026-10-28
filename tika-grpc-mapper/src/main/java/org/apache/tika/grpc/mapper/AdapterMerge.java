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
package org.apache.tika.grpc.mapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.tika.grpc.mapper.transform.MetadataTagger;
import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.EmbeddedDocument;
import org.apache.tika.grpc.v2.MetadataField;
import org.apache.tika.grpc.v2.MetadataValue;
import org.apache.tika.grpc.v2.ParseStatus;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Merges what an external parser adapter contributed into Tika's {@link Document}, under
 * rules the server enforces whatever the adapter wrote (TIKA-4771):
 * <ul>
 *     <li>Tika's own fields, status, origin, id and extra keys are never changed.</li>
 *     <li>Parent keys are added to {@code extra} only when they start with the adapter's key
 *     prefix, ignoring case, and Tika's parse recorded no key of that name, whether it ended up in a typed
 *     field or in {@code extra}; any other is dropped, with a warning on the adapter's slot.
 *     An adapter's prefix may not overlap {@link #TIKA_NAMESPACES}, so an adapter cannot
 *     write keys a consumer would read as Tika's.</li>
 *     <li>Each child becomes an {@link EmbeddedDocument} with the adapter's producer id and
 *     the path the adapter gave it, in the adapter's order, typed through Tika's Property
 *     model, up to the {@link Limits}. A child whose path is missing, does not start with
 *     {@code /}, or repeats one already used by this producer in the Document is dropped. A
 *     list cut short by a limit, or shorter than the adapter reported, is marked truncated
 *     with both counts. The first child a limit stops ends the list, so the children kept
 *     are the adapter's, in order, up to that point (less any dropped for their path).</li>
 *     <li>Keys that are not plain names, keys past the per-document limit, and value text
 *     past the length limit are dropped or cut, with a count in the warnings.</li>
 * </ul>
 */
public final class AdapterMerge {

    /** What one adapter's contribution may add. */
    public record Limits(int maxChildren, int maxKeysPerDocument, int maxValueChars,
                         long maxEmbeddedBytes) {
        public static final Limits DEFAULTS = new Limits(100_000, 256, 8_192, 64L * 1024 * 1024);
    }

    /**
     * One child the adapter made.
     *
     * @param metadata its metadata; the merge reads it and never changes it
     * @param errors   what went wrong reading it; the child is then {@code STATUS_PARTIAL}
     */
    public record Child(String path, Metadata metadata, List<String> errors) {
    }

    /**
     * @param document  Tika's Document with the contribution merged in
     * @param warnings  for the adapter's slot
     * @param reported  how many children the adapter reported
     * @param kept      how many of them the Document carries
     * @param truncated whether {@code kept < reported}
     */
    public record Result(Document document, List<String> warnings, long reported, long kept,
                         boolean truncated) {
    }

    private static final int MAX_KEY_CHARS = 256;

    /**
     * Namespaces Tika's parsers write that tika-core declares no {@link Property} for, lower
     * case. {@link #TIKA_NAMESPACES} adds the namespace of every Property tika-core's
     * metadata classes declare.
     */
    private static final List<String> PARSER_NAMESPACES = List.of(
            "tk:", "x-tika", "tika:", "tika_", "content-", "resourcename", "embedded", "pdf:",
            "pdfa:", "pdfaid:", "pdfvt:", "pdfx:", "pdfuaid:", "xmp", "meta:", "cp:",
            "extended-properties:", "custom:", "office:", "msoffice:", "doc:",
            "access_permission:", "image", "html:", "message", "mapi:", "email:", "rendering:",
            "imagereader:", "ooxml:", "odf:", "rtf_", "pst:", "mbox:", "zip:", "archive:",
            "language", "x-parsed-by", "sha256", "digest:", "digest-", "creativecommons:",
            "mp4:", "mp4-", "audio:", "video:", "ner:", "ner-", "fs:", "quicktime:",
            "machine:", "epub", "font", "external-process");

    /** tika-core's metadata classes, whose {@link Property} constants name Tika's keys. */
    private static final List<String> METADATA_CLASSES = List.of(
            "AccessPermissions", "Audio", "ClimateForecast", "DWG", "Database", "DublinCore",
            "Epub", "ExternalProcess", "FileSystem", "Font", "Geographic", "Google", "HTML",
            "HttpHeaders", "IDML", "IPTC", "ISO19115", "Icon", "MAPI", "MIF", "MachineMetadata",
            "Message", "Office", "OfficeOpenXMLCore", "OfficeOpenXMLExtended", "OneNote", "PDF",
            "PST", "PageAnchoring", "PagedText", "Photoshop", "QuattroPro", "QuickTime",
            "RTFMetadata", "Rendering", "TIFF", "TikaCoreProperties", "TikaPagedText", "Video",
            "WARC", "WordPerfect", "XMP", "XMPDC", "XMPDM", "XMPIdq", "XMPMM", "XMPPDF",
            "XMPRights", "XMPTIFF", "Zip");

    /**
     * Key namespaces Tika's own parsers and Document fields use, lower case: those of
     * {@link #PARSER_NAMESPACES} and of every {@link Property} constant in tika-core's
     * metadata classes, so a namespace tika-core adds is covered without editing this class.
     * An adapter's key prefix may neither start with one of these nor be the start of one,
     * ignoring case. {@code warc:} is not here: on the parent it is free for a WARC adapter,
     * since Tika's WARCParser writes {@code warc:} keys only on records.
     */
    public static final List<String> TIKA_NAMESPACES = namespaces();

    private static List<String> namespaces() {
        Set<String> all = new TreeSet<>(PARSER_NAMESPACES);
        for (String name : METADATA_CLASSES) {
            Class<?> type;
            try {
                type = Class.forName("org.apache.tika.metadata." + name, true,
                        AdapterMerge.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                // An older or newer tika-core without this class.
                continue;
            }
            for (Field field : type.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        || !Property.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    Property property = (Property) field.get(null);
                    if (property != null) {
                        String namespace = namespaceOf(property.getName());
                        if (!namespace.isEmpty()) {
                            all.add(namespace);
                        }
                    }
                } catch (IllegalAccessException e) {
                    // public fields of a public class: not reached
                }
            }
        }
        all.remove("warc:");
        return List.copyOf(all);
    }

    /**
     * The namespace of a key, lower case: up to and including its first {@code :}, or else
     * its first {@code -}, or else the whole key.
     */
    static String namespaceOf(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        if (colon > 0) {
            return lower.substring(0, colon + 1);
        }
        int dash = lower.indexOf('-');
        return dash > 0 ? lower.substring(0, dash + 1) : lower;
    }

    /**
     * The entry of {@link #TIKA_NAMESPACES} that {@code prefix} overlaps, ignoring case, or
     * {@code null} if none.
     */
    public static String tikaNamespace(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String namespace : TIKA_NAMESPACES) {
            if (lower.startsWith(namespace) || namespace.startsWith(lower)) {
                return namespace;
            }
        }
        return null;
    }

    private AdapterMerge() {
    }

    /**
     * @param keyPrefix        the adapter's prefix for parent keys
     * @param tikaKeys         the names in the metadata Tika's parse recorded, typed or not
     * @param parentKeys       the parent keys the adapter wrote, with their values
     * @param children         the adapter's children, in its order
     * @param reportedChildren how many children the reply described
     */
    public static Result merge(Document core, String producerId, String keyPrefix,
                               Collection<String> tikaKeys,
                               Map<String, List<String>> parentKeys, List<Child> children,
                               long reportedChildren, Limits limits) {
        List<String> warnings = new ArrayList<>();
        Counts counts = new Counts();
        Document.Builder merged = core.toBuilder();
        mergeParent(merged, keyPrefix, tikaKeys, parentKeys, limits, counts, warnings);

        Set<String> paths = new HashSet<>();
        for (EmbeddedDocument existing : merged.getEmbeddedList()) {
            if (existing.getProducerId().equals(producerId)) {
                paths.add(existing.getPath());
            }
        }
        long bytes = 0;
        int kept = 0;
        String stoppedBy = null;
        for (Child child : children) {
            String path = child.path();
            if (path == null || !path.startsWith("/") || path.length() > limits.maxValueChars()) {
                counts.badPaths++;
                continue;
            }
            if (!paths.add(path)) {
                counts.duplicatePaths++;
                continue;
            }
            if (kept == limits.maxChildren()) {
                stoppedBy = "maxChildren";
                break;
            }
            Metadata metadata = bound(child.metadata(), limits, counts);
            EmbeddedDocument.Builder embedded = DocumentBuilder.buildEmbedded(metadata)
                    .setPath(path)
                    .setProducerId(producerId)
                    .setStatus(child.errors().isEmpty()
                            ? ParseStatus.Status.STATUS_SUCCESS
                            : ParseStatus.Status.STATUS_PARTIAL);
            for (String error : child.errors()) {
                embedded.addErrors(cutValue(error, limits, counts));
            }
            EmbeddedDocument built = embedded.build();
            if (bytes + built.getSerializedSize() > limits.maxEmbeddedBytes()) {
                stoppedBy = "maxEmbeddedBytes";
                break;
            }
            bytes += built.getSerializedSize();
            merged.addEmbedded(built);
            kept++;
        }
        long reported = Math.max(reportedChildren, children.size());
        if (stoppedBy != null) {
            warnings.add("kept " + kept + " of " + reported
                    + " embedded documents (" + stoppedBy + ")");
        } else if (kept < reported) {
            warnings.add("kept " + kept + " of the " + reported
                    + " embedded documents the reply described");
        }
        counts.report(warnings, limits, keyPrefix);
        return new Result(merged.build(), List.copyOf(warnings), reported, kept,
                kept < reported);
    }

    private static void mergeParent(Document.Builder merged, String keyPrefix,
                                    Collection<String> tikaKeys,
                                    Map<String, List<String>> parentKeys, Limits limits,
                                    Counts counts, List<String> warnings) {
        // Tika's keys whether DocumentBuilder typed them or left them in extra, so an adapter
        // never adds a value next to one of Tika's typed fields.
        Set<String> taken = new HashSet<>();
        for (String key : tikaKeys) {
            taken.add(key.toLowerCase(Locale.ROOT));
        }
        for (MetadataField field : merged.getExtraList()) {
            taken.add(field.getKey().toLowerCase(Locale.ROOT));
        }
        int added = 0;
        for (Map.Entry<String, List<String>> entry : parentKeys.entrySet()) {
            String key = entry.getKey();
            if (!key.regionMatches(true, 0, keyPrefix, 0, keyPrefix.length())
                    || !plainKey(key)) {
                counts.outsidePrefix++;
                continue;
            }
            if (taken.contains(key.toLowerCase(Locale.ROOT))) {
                warnings.add("kept Tika's " + key);
                continue;
            }
            if (added == limits.maxKeysPerDocument()) {
                counts.overKeyLimit++;
                continue;
            }
            Metadata values = new Metadata();
            for (String value : entry.getValue()) {
                values.add(key, cutValue(value, limits, counts));
            }
            MetadataValue tagged = MetadataTagger.tag(values, key);
            if (tagged != null) {
                merged.addExtra(MetadataField.newBuilder().setKey(key).setValue(tagged));
                added++;
            }
        }
    }

    /**
     * A bounded copy of {@code metadata}, which is not changed: without the path key, keys
     * past the limit or that are not plain names, and with long values cut.
     */
    private static Metadata bound(Metadata metadata, Limits limits, Counts counts) {
        Metadata copy = new Metadata();
        // The path is the EmbeddedDocument's own field.
        String pathKey = TikaCoreProperties.EMBEDDED_RESOURCE_PATH.getName();
        int keys = 0;
        for (String name : metadata.names()) {
            if (name.equals(pathKey)) {
                continue;
            }
            if (!plainKey(name)) {
                counts.badKeys++;
                continue;
            }
            if (++keys > limits.maxKeysPerDocument()) {
                counts.overKeyLimit++;
                continue;
            }
            for (String value : metadata.getValues(name)) {
                copy.addTrusted(name, cutValue(value, limits, counts));
            }
        }
        return copy;
    }

    /** A key of printable characters, without spaces, of reasonable length. */
    private static boolean plainKey(String key) {
        if (key.isEmpty() || key.length() > MAX_KEY_CHARS) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c <= ' ' || c == 0x7f) {
                return false;
            }
        }
        return true;
    }

    private static String cutValue(String value, Limits limits, Counts counts) {
        if (value != null && value.length() > limits.maxValueChars()) {
            counts.cutValues++;
            return value.substring(0, limits.maxValueChars());
        }
        return value;
    }

    private static final class Counts {
        int outsidePrefix;
        int badPaths;
        int duplicatePaths;
        int badKeys;
        int overKeyLimit;
        int cutValues;

        void report(List<String> warnings, Limits limits, String keyPrefix) {
            if (outsidePrefix > 0) {
                warnings.add("dropped " + outsidePrefix + " parent keys outside " + keyPrefix);
            }
            if (badPaths > 0) {
                warnings.add("dropped " + badPaths + " embedded documents whose path is missing,"
                        + " does not start with / or is too long");
            }
            if (duplicatePaths > 0) {
                warnings.add("dropped " + duplicatePaths + " embedded documents whose path this"
                        + " producer already used");
            }
            if (badKeys > 0) {
                warnings.add("dropped " + badKeys + " keys that are not plain names");
            }
            if (overKeyLimit > 0) {
                warnings.add("dropped " + overKeyLimit + " keys over the limit of "
                        + limits.maxKeysPerDocument() + " per document");
            }
            if (cutValues > 0) {
                warnings.add("cut " + cutValues + " values to " + limits.maxValueChars()
                        + " characters");
            }
        }
    }
}
