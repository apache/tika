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

import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.FileResourceCrawler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Class that "crawls" a list of files.
 */
public class FSListCrawler extends FileResourceCrawler {

    private final BufferedReader reader;
    private final File root;

    public FSListCrawler(ArrayBlockingQueue<FileResource> fileQueue,
                         int numConsumers, File root, File list, String encoding)
            throws FileNotFoundException, UnsupportedEncodingException {
        super(fileQueue, numConsumers);
        reader = new BufferedReader(new InputStreamReader(new FileInputStream(list), encoding));
        this.root = root;

    }

    public void start() throws InterruptedException {
        String line = nextLine();

        while (line != null) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("file adder interrupted");
            }
            File f = new File(root, line);
            if (! f.exists()) {
                logger.warn("File doesn't exist:"+f.getAbsolutePath());
                line = nextLine();
                continue;
            }
            if (f.isDirectory()) {
                logger.warn("File is a directory:"+f.getAbsolutePath());
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
