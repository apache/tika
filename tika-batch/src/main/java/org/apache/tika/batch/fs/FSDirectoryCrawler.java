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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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

    private final File root;
    private final File startDirectory;
    private final Comparator<File> fileComparator = new FileNameComparator();
    private CRAWL_ORDER crawlOrder;

    public FSDirectoryCrawler(ArrayBlockingQueue<FileResource> fileQueue,
                              int numConsumers, File root, CRAWL_ORDER crawlOrder) {
        super(fileQueue, numConsumers);
        this.root = root;
        this.startDirectory = root;
        this.crawlOrder = crawlOrder;
        if (! startDirectory.isDirectory()) {
            throw new RuntimeException("Crawler couldn't find this directory:" + startDirectory.getAbsolutePath());
        }

    }

    public FSDirectoryCrawler(ArrayBlockingQueue<FileResource> fileQueue,
                              int numConsumers, File root, File startDirectory,
                              CRAWL_ORDER crawlOrder) {
        super(fileQueue, numConsumers);
        this.root = root;
        this.startDirectory = startDirectory;
        this.crawlOrder = crawlOrder;
        assert(FSUtil.checkThisIsAncestorOfOrSameAsThat(root, startDirectory));
        if (! startDirectory.isDirectory()) {
            throw new RuntimeException("Crawler couldn't find this directory:" + startDirectory.getAbsolutePath());
        }
    }

    public void start() throws InterruptedException {
        addFiles(startDirectory);
    }

    private void addFiles(File directory) throws InterruptedException {

        if (directory == null ||
                !directory.isDirectory() || !directory.canRead()) {
            String path = "null path";
            if (directory != null) {
                path = directory.getAbsolutePath();
            }
            logger.warn("FSFileAdder can't read this directory: " + path);
            return;
        }

        List<File> directories = new ArrayList<File>();
        File[] fileArr = directory.listFiles();
        if (fileArr == null) {
            logger.info("Empty directory: " + directory.getAbsolutePath());
            return;
        }

        List<File> files = new ArrayList<File>(Arrays.asList(fileArr));

        if (crawlOrder == CRAWL_ORDER.RANDOM) {
            Collections.shuffle(files);
        } else if (crawlOrder == CRAWL_ORDER.SORTED) {
            Collections.sort(files, fileComparator);
        }

        int numFiles = 0;
        for (File f : files) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("file adder interrupted");
            }

            if (f.isFile()) {
                numFiles++;
                if (numFiles == 1) {
                    handleFirstFileInDirectory(f);
                }
            }
            if (f.isDirectory()) {
                directories.add(f);
                continue;
            }
            int added = tryToAdd(new FSFileResource(root, f));
            if (added == FileResourceCrawler.STOP_NOW) {
                logger.debug("crawler has hit a limit: "+f.getAbsolutePath() + " : " + added);
                return;
            }
            logger.debug("trying to add: "+f.getAbsolutePath() + " : " + added);
        }

        for (File f : directories) {
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
    public void handleFirstFileInDirectory(File f) {
        //no-op
    }

    //simple lexical order for the file name, we don't really care about localization.
    //we do want this, though, because file.compareTo behaves differently
    //on different OS's.
    private class FileNameComparator implements Comparator<File> {

        @Override
        public int compare(File f1, File f2) {
            if (f1 == null || f2 == null) {
                return 0;
            }
            return f1.getName().compareTo(f2.getName());
        }
    }
}
