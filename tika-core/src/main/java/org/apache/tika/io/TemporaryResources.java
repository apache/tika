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
import java.util.LinkedList;
import java.util.List;

import org.apache.tika.exception.TikaException;

/**
 * Utility class for tracking and ultimately closing or otherwise disposing
 * a collection of temporary resources.
 * <p>
 * Note that this class is not thread-safe.
 *
 * @since Apache Tika 0.10
 */
public class TemporaryResources implements Closeable {

    /**
     * Tracked resources in LIFO order.
     */
    private final LinkedList<Closeable> resources = new LinkedList<Closeable>();

    /**
     * Directory for temporary files, <code>null</code> for the system default.
     */
    private File tmp = null;

    /**
     * Sets the directory to be used for the temporary files created by
     * the {@link #createTemporaryFile()} method.
     *
     * @param tmp temporary file directory,
     *            or <code>null</code> for the system default
     */
    public void setTemporaryFileDirectory(File tmp) {
        this.tmp = tmp;
    }

    /**
     * Creates and returns a temporary file that will automatically be
     * deleted when the {@link #close()} method is called.
     *
     * @return
     * @throws IOException
     */
    public File createTemporaryFile() throws IOException {
        final File file = File.createTempFile("apache-tika-", ".tmp", tmp);
        addResource(new Closeable() {
            public void close() throws IOException {
                if (!file.delete()) {
                    throw new IOException(
                            "Could not delete temporary file "
                            + file.getPath());
                }
            }
        });
        return file;
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
     * Any thrown exceptions from managed resources are collected and
     * then re-thrown only once all the resources have been closed.
     *
     * @throws IOException if one or more of the tracked resources
     *                     could not be closed
     */
    public void close() throws IOException {
        // Release all resources and keep track of any exceptions
        List<IOException> exceptions = new LinkedList<IOException>();
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (IOException e) {
                exceptions.add(e);
            }
        }
        resources.clear();

        // Throw any exceptions that were captured from above
        if (!exceptions.isEmpty()) {
            if (exceptions.size() == 1) {
                throw exceptions.get(0);
            } else {
                throw new IOExceptionWithCause(
                        "Multiple IOExceptions" + exceptions,
                        exceptions.get(0));
            }
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
