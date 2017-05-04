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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.FileResourceCrawler;

public class FSDirectoryCrawler extends FileResourceCrawler {

    public enum CRAWL_ORDER
    {
        SORTED, //alphabetical order; necessary for cross-platform unit tests
        RANDOM, //shuffle
        OS_ORDER //operating system chooses
    }

    private final Path root;
    private final Path startDirectory;
    private final Comparator<Path> pathComparator = new FileNameComparator();
    private CRAWL_ORDER crawlOrder;

    public FSDirectoryCrawler(ArrayBlockingQueue<FileResource> fileQueue,
                              int numConsumers, Path root, CRAWL_ORDER crawlOrder) {
        super(fileQueue, numConsumers);
        this.root = root;
        this.startDirectory = root;
        this.crawlOrder = crawlOrder;
        if (!Files.isDirectory(startDirectory)) {
            throw new RuntimeException("Crawler couldn't find this directory:" +
                    startDirectory.toAbsolutePath());
        }

    }

    public FSDirectoryCrawler(ArrayBlockingQueue<FileResource> fileQueue,
                              int numConsumers, Path root, Path startDirectory,
                              CRAWL_ORDER crawlOrder) {
        super(fileQueue, numConsumers);
        this.root = root;
        this.startDirectory = startDirectory;
        this.crawlOrder = crawlOrder;
        assert(startDirectory.toAbsolutePath().startsWith(root.toAbsolutePath()));

        if (! Files.isDirectory(startDirectory)) {
            throw new RuntimeException("Crawler couldn't find this directory:" + startDirectory.toAbsolutePath());
        }
    }

    public void start() throws InterruptedException {
        addFiles(startDirectory);
    }

    private void addFiles(Path directory) throws InterruptedException {

        if (directory == null) {
            LOG.warn("FSFileAdder asked to process null directory?!");
            return;
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(directory)){
            for (Path p : ds) {
                files.add(p);
            }
        } catch (IOException e) {
            LOG.warn("FSFileAdder couldn't read {}: {}", directory.toAbsolutePath(), e.getMessage(), e);
        }
        if (files.size() == 0) {
            LOG.info("Empty directory: {}", directory.toAbsolutePath());
            return;
        }


        if (crawlOrder == CRAWL_ORDER.RANDOM) {
            Collections.shuffle(files);
        } else if (crawlOrder == CRAWL_ORDER.SORTED) {
            Collections.sort(files, pathComparator);
        }

        int numFiles = 0;
        List<Path> directories = new LinkedList<>();
        for (Path f : files) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("file adder interrupted");
            }
            if (!Files.isReadable(f)) {
                LOG.warn("Skipping -- {} -- file/directory is not readable", f.toAbsolutePath());
                continue;
            }
            if (Files.isDirectory(f)) {
                directories.add(f);
                continue;
            }
            numFiles++;
            if (numFiles == 1) {
                handleFirstFileInDirectory(f);
            }
            int added = tryToAdd(new FSFileResource(root, f));
            if (added == FileResourceCrawler.STOP_NOW) {
                LOG.debug("crawler has hit a limit: {} : {}", f.toAbsolutePath(), added);
                return;
            }
            LOG.debug("trying to add: {} : {}", f.toAbsolutePath(), added);
        }

        for (Path f : directories) {
            addFiles(f);
        }
    }

    /**
     * Override this if you have any special handling
     * for the first actual file that the crawler comes across
     * in a directory.  For example, it might be handy to call
     * mkdirs() on an output directory if your FileResourceConsumers
     * are writing to a file.
     *
     * @param f file to handle
     */
    public void handleFirstFileInDirectory(Path f) {
        //no-op
    }

    //simple lexical order for the file name, we don't really care about localization.
    //we do want this, though, because file.compareTo behaves differently
    //on different OS's.
    private class FileNameComparator implements Comparator<Path> {

        @Override
        public int compare(Path f1, Path f2) {
            if (f1 == null || f2 == null) {
                return 0;
            }
            return f1.getFileName().toString().compareTo(f2.getFileName().toString());
        }
    }
}
