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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;

/**
 * A document under a fixed name in a directory of its own, for handing to an external
 * program or a library that opens files by path.
 * <p>
 * The file is a hard link where the file system allows one; else a copy if the document is
 * small, a symbolic link if it is large, and a copy if neither link can be made. Closing
 * removes the directory and whatever the reader left in it; the source keeps its content.
 */
public final class IsolatedInput implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(IsolatedInput.class);
    // above this a copy costs more than it is worth; a symbolic link is used instead
    private static final long MAX_SMALL_COPY = 64L * 1024 * 1024;
    private static final Pattern EXTENSION = Pattern.compile("(\\.[A-Za-z0-9]{1,16})?");

    private final Path dir;
    private final Path path;

    private IsolatedInput(Path dir, Path path) {
        this.dir = dir;
        this.path = path;
    }

    public static IsolatedInput of(Path source) throws IOException {
        return of(source, "");
    }

    /**
     * The file gets an extension of the media type Tika gave the document, since some readers
     * need one: the document's own extension if it is one of that type's, else the type's
     * usual one.
     */
    public static IsolatedInput of(Path source, Metadata metadata) throws IOException {
        return of(source, extensionFor(metadata));
    }

    static String extensionFor(Metadata metadata) {
        MediaType type = MediaType.parse(metadata.get(HttpHeaders.CONTENT_TYPE));
        if (type == null) {
            return "";
        }
        try {
            MimeType registered = MimeTypes.getDefaultMimeTypes()
                    .getRegisteredMimeType(type.getBaseType().toString());
            if (registered == null) {
                return "";
            }
            String own = FilenameUtils.getSuffixFromPath(
                    String.valueOf(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY)));
            for (String known : registered.getExtensions()) {
                if (known.equalsIgnoreCase(own) && EXTENSION.matcher(own).matches()) {
                    return own;
                }
            }
            String extension = registered.getExtension();
            return EXTENSION.matcher(extension).matches() ? extension : "";
        } catch (MimeTypeException e) {
            return "";
        }
    }

    /**
     * @param extension appended to the fixed name, for a reader that needs one
     */
    public static IsolatedInput of(Path source, String extension) throws IOException {
        if (!EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("not a file extension: " + extension);
        }
        return of(source, extension, Placement.values());
    }

    static IsolatedInput of(Path source, String extension, Placement... placements)
            throws IOException {
        Path dir = Files.createTempDirectory("tika-input-");
        Path target = dir.resolve("input" + extension);
        try {
            // the file itself, not a link to it
            place(source.toRealPath(), target, placements);
        } catch (IOException | RuntimeException e) {
            FileUtils.deleteQuietly(dir.toFile());
            throw e;
        }
        return new IsolatedInput(dir, target);
    }

    /** In the order tried. */
    enum Placement {
        HARD_LINK, SMALL_COPY, SYMBOLIC_LINK, COPY
    }

    private static void place(Path source, Path target, Placement[] placements)
            throws IOException {
        IOException last = null;
        for (Placement placement : placements) {
            try {
                switch (placement) {
                    case HARD_LINK:
                        Files.createLink(target, source);
                        return;
                    case SMALL_COPY:
                        if (Files.size(source) > MAX_SMALL_COPY) {
                            continue;
                        }
                        Files.copy(source, target);
                        return;
                    case SYMBOLIC_LINK:
                        Files.createSymbolicLink(target, source);
                        return;
                    default:
                        Files.copy(source, target);
                        return;
                }
            } catch (IOException e) {
                // another file system, a file this user may not link to, no links here
                last = e;
            } catch (UnsupportedOperationException e) {
                last = new IOException(e);
            }
        }
        throw last != null ? last : new IOException("no way to place " + source);
    }

    /**
     * @return the document, as an absolute path
     */
    public Path getPath() {
        return path;
    }

    @Override
    public void close() {
        try {
            // does not follow links
            FileUtils.deleteDirectory(dir.toFile());
        } catch (IOException e) {
            LOG.warn("couldn't delete {}", dir, e);
        }
    }
}
