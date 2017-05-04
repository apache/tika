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

package org.apache.tika.example;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import javax.jcr.Item;

import org.apache.jackrabbit.server.io.DefaultIOListener;
import org.apache.jackrabbit.server.io.IOListener;
import org.apache.jackrabbit.server.io.IOUtil;
import org.apache.jackrabbit.server.io.ImportContext;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>ImportContextImpl</code>...
 */
public class ImportContextImpl implements ImportContext {
    private static final Logger LOG = LoggerFactory.getLogger(ImportContextImpl.class);

    private final IOListener ioListener;
    private final Item importRoot;
    private final String systemId;
    private final File inputFile;

    private InputContext inputCtx;
    private boolean completed;

    private final Detector detector;

    private final MediaType type;

    /**
     * Creates a new item import context. The specified InputStream is written
     * to a temporary file in order to avoid problems with multiple IOHandlers
     * that try to run the import but fail. The temporary file is deleted as
     * soon as this context is informed that the import has been completed and
     * it will not be used any more.
     *
     * @param importRoot
     * @param systemId
     * @param ctx        input context, or <code>null</code>
     * @param stream     document input stream, or <code>null</code>
     * @param ioListener
     * @param detector   content type detector
     * @throws IOException
     * @see ImportContext#informCompleted(boolean)
     */
    public ImportContextImpl(Item importRoot, String systemId,
                             InputContext ctx, InputStream stream, IOListener ioListener,
                             Detector detector) throws IOException {
        this.importRoot = importRoot;
        this.systemId = systemId;
        this.inputCtx = ctx;
        this.ioListener = (ioListener != null) ? ioListener
                : new DefaultIOListener(LOG);
        this.detector = detector;

        Metadata metadata = new Metadata();
        if (ctx != null && ctx.getContentType() != null) {
            metadata.set(Metadata.CONTENT_TYPE, ctx.getContentType());
        }
        if (systemId != null) {
            metadata.set(Metadata.RESOURCE_NAME_KEY, systemId);
        }
        if (stream != null && !stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }
        type = detector.detect(stream, metadata);

        this.inputFile = IOUtil.getTempFile(stream);
    }

    /**
     * @see ImportContext#getIOListener()
     */
    public IOListener getIOListener() {
        return ioListener;
    }

    /**
     * @see ImportContext#getImportRoot()
     */
    public Item getImportRoot() {
        return importRoot;
    }

    /**
     * @see ImportContext#getDetector()
     */
    public Detector getDetector() {
        return detector;
    }

    /**
     * @see ImportContext#hasStream()
     */
    public boolean hasStream() {
        return inputFile != null;
    }

    /**
     * Returns a new <code>InputStream</code> to the temporary file created
     * during instanciation or <code>null</code>, if this context does not
     * provide a stream.
     *
     * @see ImportContext#getInputStream()
     * @see #hasStream()
     */
    public InputStream getInputStream() {
        checkCompleted();
        InputStream in = null;
        if (inputFile != null) {
            try {
                in = new FileInputStream(inputFile);
            } catch (IOException e) {
                // unexpected error... ignore and return null
            }
        }
        return in;
    }

    /**
     * @see ImportContext#getSystemId()
     */
    public String getSystemId() {
        return systemId;
    }

    /**
     * @see ImportContext#getModificationTime()
     */
    public long getModificationTime() {
        return (inputCtx != null) ? inputCtx.getModificationTime() : new Date().getTime();
    }

    /**
     * @see ImportContext#getContentLanguage()
     */
    public String getContentLanguage() {
        return (inputCtx != null) ? inputCtx.getContentLanguage() : null;
    }

    /**
     * @see ImportContext#getContentLength()
     */
    public long getContentLength() {
        long length = IOUtil.UNDEFINED_LENGTH;
        if (inputCtx != null) {
            length = inputCtx.getContentLength();
        }
        if (length < 0 && inputFile != null) {
            length = inputFile.length();
        }
        if (length < 0) {
            LOG.debug("Unable to determine content length -> default value = {}", IOUtil.UNDEFINED_LENGTH);
        }
        return length;
    }

    /**
     * @see ImportContext#getMimeType()
     */
    public String getMimeType() {
        return IOUtil.getMimeType(type.toString());
    }

    /**
     * @see ImportContext#getEncoding()
     */
    public String getEncoding() {
        return IOUtil.getEncoding(type.toString());
    }

    /**
     * @see ImportContext#getProperty(Object)
     */
    public Object getProperty(Object propertyName) {
        return (inputCtx != null) ? inputCtx.getProperty(propertyName.toString()) : null;
    }

    /**
     * @see ImportContext#informCompleted(boolean)
     */
    public void informCompleted(boolean success) {
        checkCompleted();
        completed = true;
        if (inputFile != null) {
            inputFile.delete();
        }
    }

    /**
     * @see ImportContext#isCompleted()
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * @throws IllegalStateException if the context is already completed.
     * @see #isCompleted()
     * @see #informCompleted(boolean)
     */
    private void checkCompleted() {
        if (completed) {
            throw new IllegalStateException("ImportContext has already been consumed.");
        }
    }
}
