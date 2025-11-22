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
package org.apache.tika.plugins;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.pf4j.util.Unzip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadSafeUnzipper {
    private static final Logger LOG = LoggerFactory.getLogger(TikaPluginManager.class);

    private static final long MAX_WAIT_MS = 60000;

    public static synchronized void unzipPlugin(Path source) throws IOException {
        if (! source.getFileName().toString().endsWith(".zip")) {
            throw new IllegalArgumentException("source file name must end in '.zip'");
        }
        File lockFile = new File(source.toAbsolutePath() + ".lock");
        FileChannel fileChannel = null;
        FileLock fileLock = null;
        List<IOException> exceptions = new ArrayList<>();
        try {
            fileChannel = new RandomAccessFile(lockFile, "rw").getChannel();
            LOG.debug("acquiring lock");
            fileLock = fileChannel.lock();
            LOG.debug("acquired lock");
            if (isExtracted(source)) {
                LOG.debug("{} is already extracted", source);
                return;
            }
            extract(source);
        } finally {
            if (fileLock != null && fileLock.isValid()) {
                try {
                    fileLock.release();
                } catch (IOException e) {
                    LOG.warn("failed to release the lock");
                    exceptions.add(e);
                }
            }
            if (fileChannel != null) {
                try {
                    fileChannel.close();
                } catch (IOException e) {
                    LOG.warn("failed to close the file channel");
                    exceptions.add(e);
                }
            }
            boolean isDeleted = lockFile.delete();
            if (! isDeleted) {
                LOG.warn("failed to delete the lock file");
                exceptions.add(new IOException("failed to delete lock file: " + lockFile));
            }
        }
        if (! exceptions.isEmpty()) {
            throw exceptions.get(0);
        }
    }

    private static void extract(Path source) throws IOException {
        Path destination = getDestination(source);
        Unzip unzip = new Unzip(source.toFile(), destination.toFile());
        unzip.extract();
    }

    private static boolean isExtracted(Path source) {
        Path destination = getDestination(source);
        return Files.isDirectory(destination);
    }

    private static Path getDestination(Path source) {
        String fName = source.getFileName().toString();
        fName = fName.substring(0, fName.length() - 4);
        return source.toAbsolutePath().getParent().resolve(fName);
    }
}
