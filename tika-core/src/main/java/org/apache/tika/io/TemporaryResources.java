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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            } catch (IOException e) {
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
        // Release all resources and keep track of any exceptions
        IOException exception = null;
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (IOException e) {
                if (exception == null) {
                    exception = e;
                } else {
                    exception.addSuppressed(e);
                }
            }
        }
        resources.clear();

        // Throw any exceptions that were captured from above
        if (exception != null) {
            throw exception;
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
