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
package org.apache.tika.eval.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.Tika;
import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.fs.FSProperties;
import org.apache.tika.detect.FileCommandDetector;
import org.apache.tika.eval.app.db.ColInfo;
import org.apache.tika.eval.app.db.Cols;
import org.apache.tika.eval.app.db.TableInfo;
import org.apache.tika.eval.app.io.IDBWriter;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

/**
 * This class profiles actual files as opposed to extracts e.g. {@link ExtractProfiler}.
 * This does _not_ parse files, but does run file type identification and digests the
 * raw bytes.
 * <p>
 * If the 'file' command is available on the command line, this will also run the
 * FileCommandDetector.
 */

public class FileProfiler extends AbstractProfiler {
//TODO: we should allow users to select digest type/encoding and file detector(s).

    public static final String DETECT_EXCEPTION = "detect-exception";
    private static final boolean HAS_FILE = FileCommandDetector.checkHasFile();
    private static final Logger LOG = LoggerFactory.getLogger(FileProfiler.class);
    private static final Tika TIKA = new Tika();
    private static final FileCommandDetector FILE_COMMAND_DETECTOR = new FileCommandDetector();
    public static TableInfo FILE_PROFILES = HAS_FILE ? new TableInfo("file_profiles",
            new ColInfo(Cols.FILE_PATH, Types.VARCHAR, 2048, "PRIMARY KEY"),
            new ColInfo(Cols.FILE_NAME, Types.VARCHAR, 2048),
            new ColInfo(Cols.FILE_EXTENSION, Types.VARCHAR, 24),
            new ColInfo(Cols.LENGTH, Types.BIGINT), new ColInfo(Cols.SHA256, Types.VARCHAR, 64),
            new ColInfo(Cols.TIKA_MIME_ID, Types.INTEGER),
            new ColInfo(Cols.FILE_MIME_ID, Types.INTEGER)) : new TableInfo("file_profiles",
            new ColInfo(Cols.FILE_PATH, Types.VARCHAR, 2048, "PRIMARY KEY"),
            new ColInfo(Cols.FILE_NAME, Types.VARCHAR, 2048),
            new ColInfo(Cols.FILE_EXTENSION, Types.VARCHAR, 24),
            new ColInfo(Cols.LENGTH, Types.BIGINT), new ColInfo(Cols.SHA256, Types.VARCHAR, 64),
            new ColInfo(Cols.TIKA_MIME_ID, Types.INTEGER));


    public static TableInfo FILE_MIME_TABLE =
            new TableInfo("file_mimes", new ColInfo(Cols.MIME_ID, Types.INTEGER, "PRIMARY KEY"),
                    new ColInfo(Cols.MIME_STRING, Types.VARCHAR, 256),
                    new ColInfo(Cols.FILE_EXTENSION, Types.VARCHAR, 12));
    static Options OPTIONS;

    static {

        Option inputDir = new Option("inputDir", true,
                "optional: directory for original binary input documents." +
                        " If not specified, -extracts is crawled as is.");

        OPTIONS = new Options().addOption(inputDir)
                .addOption("bc", "optional: tika-batch config file")
                .addOption("numConsumers", true, "optional: number of consumer threads")
                .addOption("db", true, "db file to which to write results").addOption("jdbc", true,
                        "EXPERT: full jdbc connection string. Must specify this or -db <h2db>")
                .addOption("jdbcDriver", true, "EXPERT: jdbc driver, or specify via -Djdbc.driver")
                .addOption("tablePrefix", true, "EXPERT: optional prefix for table names")
                .addOption("drop", false, "drop tables if they exist")
                .addOption("maxFilesToAdd", true, "maximum number of files to add to the crawler")

        ;

    }

    private final Path inputDir;

    public FileProfiler(ArrayBlockingQueue<FileResource> fileQueue, Path inputDir,
                        IDBWriter dbWriter) {
        super(fileQueue, dbWriter);
        this.inputDir = inputDir;
    }

    public static void USAGE() {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp(80,
                "java -jar tika-eval-x.y.jar FileProfiler -inputDir docs -db mydb [-inputDir input]",
                "Tool: Profile", FileProfiler.OPTIONS,
                "Note: for the default h2 db, do not include the .mv.db at the end of the db name.");
    }

    @Override
    public boolean processFileResource(FileResource fileResource) {
        String relPath = fileResource.getMetadata().get(FSProperties.FS_REL_PATH);
        try (InputStream is = fileResource.openInputStream()) {
            try (TikaInputStream tis = TikaInputStream.get(is)) {
                Path path = tis.getPath();
                Map<Cols, String> data = new HashMap<>();
                int tikaMimeId = writer.getMimeId(detectTika(tis));
                String fileName = "";
                String extension = "";
                long length = -1;
                try {
                    fileName = FilenameUtils.getName(relPath);
                } catch (IllegalArgumentException e) {
                    LOG.warn("bad file name: " + relPath, e);
                }

                try {
                    extension = FilenameUtils.getExtension(relPath);
                } catch (IllegalArgumentException e) {
                    LOG.warn("bad extension: " + relPath, e);
                }

                try {
                    length = Files.size(path);
                } catch (IOException e) {
                    LOG.warn("problem getting size: " + relPath, e);
                }

                data.put(Cols.FILE_PATH, relPath);
                data.put(Cols.FILE_NAME, fileName);
                data.put(Cols.FILE_EXTENSION, extension);
                data.put(Cols.LENGTH, Long.toString(length));
                data.put(Cols.TIKA_MIME_ID, Integer.toString(tikaMimeId));
                data.put(Cols.SHA256, DigestUtils.sha256Hex(tis));
                if (HAS_FILE) {
                    int fileMimeId = writer.getMimeId(detectFile(tis));
                    data.put(Cols.FILE_MIME_ID, Integer.toString(fileMimeId));
                }
                writer.writeRow(FILE_PROFILES, data);
            }
        } catch (IOException e) {
            //log at least!
            return false;
        }
        return true;
    }

    private String detectFile(TikaInputStream tis) {
        try {
            return FILE_COMMAND_DETECTOR.detect(tis, new Metadata()).toString();
        } catch (IOException e) {
            return DETECT_EXCEPTION;
        }
    }

    private String detectTika(TikaInputStream tis) {
        try {
            return TIKA.detect(tis);
        } catch (IOException e) {
            return DETECT_EXCEPTION;
        }
    }
}
