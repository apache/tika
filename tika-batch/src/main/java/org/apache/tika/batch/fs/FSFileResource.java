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
package org.apache.tika.batch.fs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.apache.tika.batch.FileResource;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * FileSystem(FS)Resource wraps a file name.
 * <p/>
 * This class automatically sets the following keys in Metadata:
 * <ul>
 *     <li>TikaCoreProperties.RESOURCE_NAME_KEY (file name)</li>
 *     <li>Metadata.CONTENT_LENGTH</li>
 *     <li>FSProperties.FS_REL_PATH</li>
 *     <li>FileResource.FILE_EXTENSION</li>
 * </ul>,
 */
public class FSFileResource implements FileResource {

    private final Path fullPath;
    private final String relativePath;
    private final Metadata metadata;


    /**
     * Constructor
     *
     * @param inputRoot the input root for the file
     * @param fullPath  the full path to the file
     * @throws IllegalArgumentException if the fullPath is not
     *                                  a child of inputRoot
     */
    public FSFileResource(Path inputRoot, Path fullPath) {
        this.fullPath = fullPath;
        this.metadata = new Metadata();
        //child path must actually be a child
        assert (fullPath
                .toAbsolutePath()
                .startsWith(inputRoot.toAbsolutePath()));
        this.relativePath = inputRoot
                .relativize(fullPath)
                .toString();

        //need to set these now so that the filter can determine
        //whether or not to crawl this file
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fullPath
                .getFileName()
                .toString());
        long sz = -1;
        try {
            sz = Files.size(fullPath);
        } catch (IOException e) {
            //swallow
            //not existent file will be handled downstream
        }
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(sz));
        metadata.set(FSProperties.FS_REL_PATH, relativePath);
        metadata.set(FileResource.FILE_EXTENSION, getExtension(fullPath));
    }

    /**
     * Simple extension extractor that takes whatever comes after the
     * last period in the path.  It returns a lowercased version of the "extension."
     * <p>
     * If there is no period, it returns an empty string.
     *
     * @param fullPath full path from which to try to find an extension
     * @return the lowercased extension or an empty string
     */
    private String getExtension(Path fullPath) {
        String p = fullPath
                .getFileName()
                .toString();
        int i = p.lastIndexOf(".");
        if (i > -1) {
            return p
                    .substring(i + 1)
                    .toLowerCase(Locale.ROOT);
        }
        return "";
    }

    /**
     * @return file's relativePath
     */
    @Override
    public String getResourceId() {
        return relativePath;
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        //no need to include Metadata because we already set the
        //same information in the initializer
        return TikaInputStream.get(fullPath);
    }
}
