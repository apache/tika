package org.apache.tika.batch.fs;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.FileResourceCrawler;

/**
 * Class that "crawls" a list of files.
 */
public class FSListCrawler extends FileResourceCrawler {

    private final BufferedReader reader;
    private final Path root;

    /**
     *
     * @param fileQueue
     * @param numConsumers
     * @param root
     * @param list
     * @param encoding
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     * @deprecated
     * @see #FSListCrawler(ArrayBlockingQueue, int, Path, Path, Charset)
     */
    @Deprecated
    public FSListCrawler(ArrayBlockingQueue<FileResource> fileQueue,
                         int numConsumers, File root, File list, String encoding)
            throws FileNotFoundException, UnsupportedEncodingException {
        super(fileQueue, numConsumers);
        reader = new BufferedReader(new InputStreamReader(new FileInputStream(list), encoding));
        this.root = Paths.get(root.toURI());

    }

    /**
     * Constructor for a crawler that reads a list of files to process.
     * <p>
     * The list should be paths relative to the root.
     *
     * @param fileQueue queue for batch
     * @param numConsumers number of consumers
     * @param root root input director
     * @param list text file list (one file per line) of paths relative to
     *             the root for processing
     * @param charset charset of the file
     * @throws IOException
     */
    public FSListCrawler(ArrayBlockingQueue<FileResource> fileQueue,
                         int numConsumers, Path root, Path list, Charset charset)
            throws IOException {
        super(fileQueue, numConsumers);
        reader = Files.newBufferedReader(list, charset);
        this.root = root;
    }

    public void start() throws InterruptedException {
        String line = nextLine();

        while (line != null) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("file adder interrupted");
            }
            Path f = Paths.get(root.toString(), line);
            if (! Files.exists(f)) {
                LOG.warn("File doesn't exist: {}", f.toAbsolutePath());
                line = nextLine();
                continue;
            }
            if (Files.isDirectory(f)) {
                LOG.warn("File is a directory: {}", f.toAbsolutePath());
                line = nextLine();
                continue;
            }
            tryToAdd(new FSFileResource(root, f));
            line = nextLine();
        }
    }

    private String nextLine() {
        String line = null;
        try {
            line = reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return line;
    }
}
