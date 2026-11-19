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
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TempLimitReachedException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.StringUtils;

/**
 * Utility class for tracking and ultimately closing or otherwise disposing
 * a collection of temporary resources.
 * <p>
 * Note that this class is not thread-safe.
 *
 * @since Apache Tika 0.10
 */
public class TemporaryResources implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(TemporaryResources.class);

    /**
     * Tracked resources in LIFO order.
     */
    private final LinkedList<Closeable> resources = new LinkedList<>();

    /**
     * Directory for temporary files, <code>null</code> for the system default.
     */
    private Path tempFileDir = null;

    private TempBytesBudget tempBudget;

    // charged to tempBudget and not yet given back
    private long tempBytesCharged;

    /**
     * Sets the directory to be used for the temporary files created by
     * the {@link #createTempFile(String)} method.
     *
     * @param tempFileDir temporary file directory,
     *                    or <code>null</code> for the system default
     */
    public void setTemporaryFileDirectory(Path tempFileDir) {
        this.tempFileDir = tempFileDir;
    }

    /**
     * Sets the directory to be used for the temporary files created by
     * the {@link #createTempFile(String)} method.
     *
     * @param tempFileDir temporary file directory,
     *                    or <code>null</code> for the system default
     * @see #setTemporaryFileDirectory(Path)
     */
    public void setTemporaryFileDirectory(File tempFileDir) {
        this.tempFileDir = tempFileDir == null ? null : tempFileDir.toPath();
    }

    /**
     * Creates a temporary file that will automatically be deleted when
     * the {@link #close()} method is called, returning its path.
     * @param suffix -- the suffix of the file if known, starting with "." as in ".pdf"
     * @return Path to created temporary file that will be deleted after closing
     * @throws IOException
     */
    public Path createTempFile(String suffix) throws IOException {
        String actualSuffix = StringUtils.isBlank(suffix) ? ".tmp" : suffix;

        final Path path = tempFileDir == null ? Files.createTempFile("apache-tika-", actualSuffix) :
                Files.createTempFile(tempFileDir, "apache-tika-", actualSuffix);
        addResource(() -> {
            try {
                Files.delete(path);
            } catch (IOException | RuntimeException e) {
                // delete when exit if current delete fail
                LOG.warn("delete tmp file fail, will delete it on exit");
                path.toFile().deleteOnExit();
            }
        });
        return path;
    }

    public Path createTempFile() throws IOException {
        return createTempFile(StringUtils.EMPTY);
    }

    /**
     * Sets the budget that {@link #newOutputStream(Path)} charges. The first budget set
     * wins; {@code null} is ignored. Bytes written before a budget is set are not counted.
     *
     * @since Apache Tika 4.3
     */
    public void setTempBudget(TempBytesBudget tempBudget) {
        if (this.tempBudget == null) {
            this.tempBudget = tempBudget;
        }
    }

    /**
     * Opens a temp file from {@link #createTempFile(String)} for writing, charging each
     * write to the {@link TempBytesBudget}, if one is set. What was charged is given back
     * when {@link #close()} deletes the files. Use this, not {@code Files.newOutputStream},
     * for any temp file whose size the document decides.
     *
     * @throws TempLimitReachedException from a write that would exceed the budget
     * @since Apache Tika 4.3
     */
    public OutputStream newOutputStream(Path tempFile) throws IOException {
        return new ChargingOutputStream(Files.newOutputStream(tempFile));
    }

    /**
     * Charges a temp file, or everything under a temp directory, that something else
     * wrote: an external program or a library. That cannot stop the writer early; it
     * makes what it wrote count, and fails once the total is past the budget.
     *
     * @throws TempLimitReachedException if the budget cannot cover it
     * @since Apache Tika 4.3
     */
    public void chargeTempFile(Path fileOrDirectory) throws IOException {
        if (tempBudget == null || !Files.exists(fileOrDirectory)) {
            return;
        }
        long bytes;
        try (Stream<Path> paths = Files.walk(fileOrDirectory)) {
            bytes = paths.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
        }
        charge(bytes);
    }

    private void charge(long n) throws TempLimitReachedException {
        if (tempBudget == null) {
            return;
        }
        if (!tempBudget.tryReserve(n)) {
            throw new TempLimitReachedException(tempBudget.getMaxBytes());
        }
        tempBytesCharged += n;
    }

    private final class ChargingOutputStream extends FilterOutputStream {

        ChargingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            charge(1);
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            charge(len);
            out.write(b, off, len);
        }
    }

    /**
     * Creates a temporary file that will automatically be deleted when
     * the {@link #close()} method is called, returning its path.
     *
     * @return Path to created temporary file that will be deleted after closing
     * @throws IOException
     */
    public Path createTempFile(Metadata metadata) throws IOException {
        String resourceName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (StringUtils.isBlank(resourceName)) {
            return createTempFile(StringUtils.EMPTY);
        }
        return createTempFile(FilenameUtils.getSuffixFromPath(resourceName));
    }
    /**
     * Creates and returns a temporary file that will automatically be
     * deleted when the {@link #close()} method is called.
     *
     * @return Created temporary file that'll be deleted after closing
     * @throws IOException
     * @see #createTempFile(String)
     */
    public File createTemporaryFile() throws IOException {
        return createTempFile(StringUtils.EMPTY).toFile();
    }

    /**
     * Adds a new resource to the set of tracked resources that will all be
     * closed when the {@link #close()} method is called.
     *
     * @param resource resource to be tracked
     */
    public void addResource(Closeable resource) {
        resources.addFirst(resource);
    }

    /**
     * Returns the latest of the tracked resources that implements or
     * extends the given interface or class.
     *
     * @param klass interface or class
     * @return matching resource, or <code>null</code> if not found
     */
    @SuppressWarnings("unchecked")
    public <T extends Closeable> T getResource(Class<T> klass) {
        for (Closeable resource : resources) {
            if (klass.isAssignableFrom(resource.getClass())) {
                return (T) resource;
            }
        }
        return null;
    }

    /**
     * Closes all tracked resources. The resources are closed in reverse order
     * from how they were added.
     * <p>
     * Any suppressed exceptions from managed resources are collected and
     * then added to the first thrown exception, which is re-thrown once
     * all the resources have been closed.
     *
     * @throws IOException if one or more of the tracked resources
     *                     could not be closed
     */
    public void close() throws IOException {
        try {
            closeAll(resources.toArray(new Closeable[0]));
        } finally {
            resources.clear();
            if (tempBudget != null) {
                tempBudget.release(tempBytesCharged);
            }
            tempBytesCharged = 0;
        }
    }

    /**
     * Closes every closeable even when one throws, unchecked included. The first failure
     * propagates with the rest attached as suppressed; nulls are skipped.
     */
    public static void closeAll(Closeable... closeables) throws IOException {
        Throwable first = null;
        for (Closeable closeable : closeables) {
            if (closeable == null) {
                continue;
            }
            try {
                closeable.close();
            } catch (Throwable t) {
                if (first == null) {
                    first = t;
                } else if (t != first) {   // addSuppressed rejects self-suppression
                    first.addSuppressed(t);
                }
            }
        }
        if (first instanceof IOException) {
            throw (IOException) first;
        } else if (first instanceof RuntimeException) {
            throw (RuntimeException) first;
        } else if (first instanceof Error) {
            throw (Error) first;
        } else if (first != null) {
            throw new IOException(first);
        }
    }

    /**
     * Calls the {@link #close()} method and wraps the potential
     * {@link IOException} into a {@link TikaException} for convenience
     * when used within Tika.
     *
     * @throws TikaException if one or more of the tracked resources
     *                       could not be closed
     */
    public void dispose() throws TikaException {
        try {
            close();
        } catch (IOException e) {
            throw new TikaException("Failed to close temporary resources", e);
        }
    }

}
