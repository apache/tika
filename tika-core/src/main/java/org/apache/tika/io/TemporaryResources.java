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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

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

    // per file or directory: bytes written and not yet given back; counted with or without a
    // budget, so that one set late can still be charged for them
    private final Map<Path, Charge> tempBytesCharged = new HashMap<>();

    private static final class Charge {
        private long bytes;
    }

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
     * wins; {@code null} is ignored. What was already written through that method is
     * charged now; if it does not fit, the budget is left at its limit, for the next
     * write or {@link org.apache.tika.config.TempLimits#apply} to report.
     *
     * @since Apache Tika 4.3
     */
    public void setTempBudget(TempBytesBudget tempBudget) {
        if (this.tempBudget != null || tempBudget == null) {
            return;
        }
        this.tempBudget = tempBudget;
        long written = 0;
        for (Charge charge : tempBytesCharged.values()) {
            written += charge.bytes;
        }
        if (!tempBudget.tryReserve(written)) {
            // nothing was reserved, so there is nothing to give back
            tempBytesCharged.clear();
        }
    }

    /**
     * Opens a temp file for writing, charging each write to the {@link TempBytesBudget},
     * if one is set. Opening truncates, so what an earlier open of the same file charged
     * is given back first. The charge is given back by {@link #deleteTempFile(Path)}, or
     * by {@link #close()} at the latest: a caller that deletes the file some other way
     * before then leaves it counted. Use this, not {@code Files.newOutputStream}, for any
     * temp file whose size the document decides.
     *
     * @throws TempLimitReachedException from a write that would exceed the budget
     * @since Apache Tika 4.3
     */
    public OutputStream newOutputStream(Path tempFile) throws IOException {
        OutputStream out = Files.newOutputStream(tempFile);
        release(tempFile);
        Charge charge = new Charge();
        tempBytesCharged.put(tempFile, charge);
        return new ChargingOutputStream(out, charge);
    }

    /**
     * Deletes a temp file and gives back what it was charged, for a file that goes
     * before this does.
     *
     * @since Apache Tika 4.3
     */
    public void deleteTempFile(Path tempFile) throws IOException {
        try {
            Files.deleteIfExists(tempFile);
        } finally {
            release(tempFile);
        }
    }

    /**
     * Charges a temp file, or everything under a temp directory, that something else
     * wrote: an external program or a library. That cannot stop the writer early; it
     * makes what it wrote count, and fails once the total is past the budget. Measuring
     * the same path again replaces its earlier charge.
     *
     * @throws TempLimitReachedException if the budget cannot cover it
     * @since Apache Tika 4.3
     */
    public void chargeTempFile(Path fileOrDirectory) throws IOException {
        if (tempBudget == null || !Files.exists(fileOrDirectory)) {
            return;
        }
        long[] bytes = new long[1];
        // links are not followed, so one extracted file is never counted through a second name
        Files.walkFileTree(fileOrDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    bytes[0] += attrs.size();
                }
                return FileVisitResult.CONTINUE;
            }
        });
        release(fileOrDirectory);
        Charge charge = new Charge();
        charge(charge, bytes[0]);
        tempBytesCharged.put(fileOrDirectory, charge);
    }

    private void charge(Charge charge, long n) throws TempLimitReachedException {
        if (tempBudget != null && !tempBudget.tryReserve(n)) {
            throw new TempLimitReachedException(tempBudget.getMaxBytes());
        }
        charge.bytes += n;
    }

    private void release(Path path) {
        Charge charge = tempBytesCharged.remove(path);
        if (charge != null && tempBudget != null) {
            tempBudget.release(charge.bytes);
        }
    }

    private final class ChargingOutputStream extends FilterOutputStream {

        private final Charge charge;

        ChargingOutputStream(OutputStream out, Charge charge) {
            super(out);
            this.charge = charge;
        }

        @Override
        public void write(int b) throws IOException {
            charge(charge, 1);
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            charge(charge, len);
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
                for (Charge charge : tempBytesCharged.values()) {
                    tempBudget.release(charge.bytes);
                }
            }
            tempBytesCharged.clear();
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
