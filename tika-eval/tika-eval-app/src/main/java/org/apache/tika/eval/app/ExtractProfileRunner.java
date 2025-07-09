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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.eval.app.db.Cols;
import org.apache.tika.eval.app.db.JDBCUtil;
import org.apache.tika.eval.app.db.MimeBuffer;
import org.apache.tika.eval.app.db.TableInfo;
import org.apache.tika.eval.app.io.DBWriter;
import org.apache.tika.eval.app.io.ExtractReader;
import org.apache.tika.eval.app.io.ExtractReaderException;
import org.apache.tika.eval.app.io.IDBWriter;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.pipesiterator.CallablePipesIterator;
import org.apache.tika.pipes.pipesiterator.PipesIterator;
import org.apache.tika.pipes.pipesiterator.fs.FileSystemPipesIterator;

public class ExtractProfileRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ExtractProfileRunner.class);
    private static final long DIR_WALKER_COMPLETED_VALUE = 2;
    private static final long PROFILE_WORKER_COMPLETED_VALUE = 1;

    static Options OPTIONS;

    static {

        OPTIONS = new Options()
                .addOption(Option.builder("e").longOpt("extracts").hasArg().desc("required: directory of extracts").build())
                .addOption(Option.builder("i").longOpt("inputDir").hasArg().desc("optional: directory for original binary input documents."
                        + " If not specified, -extracts is crawled as is.").build())
                .addOption(Option.builder("d").longOpt("db").hasArg().desc("optional: db path").build())
                .addOption(Option.builder("c").longOpt("config").hasArg().desc("tika-eval json config file").build())
                ;
    }
    public static void main(String[] args) throws Exception {
        DefaultParser defaultCLIParser = new DefaultParser();
        CommandLine commandLine = defaultCLIParser.parse(OPTIONS, args);
        EvalConfig evalConfig = commandLine.hasOption('c') ? EvalConfig.load(Paths.get(commandLine.getOptionValue('c'))) : new EvalConfig();
        Path extractsDir = commandLine.hasOption('e') ? Paths.get(commandLine.getOptionValue('e')) : Paths.get(USAGE_FAIL("Must specify extracts dir: -i"));
        Path inputDir = commandLine.hasOption('i') ? Paths.get(commandLine.getOptionValue('i')) : extractsDir;
        String dbPath = commandLine.hasOption('d') ? commandLine.getOptionValue('d') : USAGE_FAIL("Must specify the db name: -d");
        String jdbcString = getJdbcConnectionString(dbPath);
        execute(inputDir, extractsDir, jdbcString, evalConfig);
    }

    private static String getJdbcConnectionString(String dbPath) {
        if (dbPath.startsWith("jdbc:")) {
            return dbPath;
        }
        //default to h2
        Path p = Paths.get(dbPath);
        return "jdbc:h2:file:" + p.toAbsolutePath();

    }

    private static void execute(Path inputDir, Path extractsDir, String dbPath, EvalConfig evalConfig) throws SQLException, IOException {

        //parameterize this? if necesssary
        try {
            ProfilerBase.loadCommonTokens(null, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        JDBCUtil jdbcUtil = new JDBCUtil(dbPath, evalConfig.getJdbcDriverClass());
        ExtractProfilerBuilder builder = new ExtractProfilerBuilder();
        MimeBuffer mimeBuffer = initTables(jdbcUtil, builder, dbPath, evalConfig);
        builder.populateRefTables(jdbcUtil, mimeBuffer);

        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger activeWorkers = new AtomicInteger(evalConfig.getNumWorkers());
        AtomicBoolean crawlerActive = new AtomicBoolean(true);


        ArrayBlockingQueue<FetchEmitTuple> queue = new ArrayBlockingQueue<>(1000);
        CallablePipesIterator pipesIterator = new CallablePipesIterator(createIterator(inputDir), queue);
        ExecutorService executorService = Executors.newFixedThreadPool(evalConfig.getNumWorkers() + 2);
        ExecutorCompletionService<Long> executorCompletionService = new ExecutorCompletionService<>(executorService);

        StatusReporter statusReporter = new StatusReporter(pipesIterator, processed, activeWorkers, crawlerActive);
        executorCompletionService.submit(statusReporter);

        executorCompletionService.submit(pipesIterator);
        for (int i = 0; i < evalConfig.getNumWorkers(); i++) {
            ExtractReader extractReader = new ExtractReader(ExtractReader.ALTER_METADATA_LIST.AS_IS, evalConfig.getMinExtractLength(), evalConfig.getMaxExtractLength());
            ExtractProfiler extractProfiler = new ExtractProfiler(inputDir, extractsDir, extractReader, builder.getDBWriter(builder.tableInfos, jdbcUtil, mimeBuffer));
            executorCompletionService.submit(new ProfileWorker(queue, extractProfiler, processed));
        }

        int finished = 0;
        try {
            while (finished < evalConfig.getNumWorkers() + 2) {
                //blocking
                Future<Long> future = executorCompletionService.take();
                Long result = future.get();
                if (result != null) {
                    //if the dir walker has finished
                    if (result == DIR_WALKER_COMPLETED_VALUE) {
                        queue.put(PipesIterator.COMPLETED_SEMAPHORE);
                        crawlerActive.set(false);
                    } else if (result == PROFILE_WORKER_COMPLETED_VALUE) {
                        activeWorkers.decrementAndGet();
                    }
                    finished++;
                }
            }
        } catch (InterruptedException e) {
            LOG.info("interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            mimeBuffer.close();
            executorService.shutdownNow();
        }

    }

    private static PipesIterator createIterator(Path inputDir) {
        FileSystemPipesIterator fs = new FileSystemPipesIterator(inputDir);
        fs.setFetcherName("");
        fs.setEmitterName("");
        return fs;
    }

    private static MimeBuffer initTables(JDBCUtil jdbcUtil, ExtractProfilerBuilder builder, String connectionString, EvalConfig evalConfig) throws SQLException, IOException {

        //step 1. create the tables
        jdbcUtil.createTables(builder.getNonRefTableInfos(), JDBCUtil.CREATE_TABLE.THROW_EX_IF_EXISTS);
        jdbcUtil.createTables(builder.getRefTableInfos(), JDBCUtil.CREATE_TABLE.THROW_EX_IF_EXISTS);

        //step 2. create mime buffer
        return new MimeBuffer(jdbcUtil.getConnection(), builder.getMimeTable(), TikaConfig.getDefaultConfig());
    }

    private static void USAGE() {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp(80, "java -jar tika-eval-app-x.y.z.jar FileProfiler -e docs -d mydb [-i inputDir, -c config.json]",
                "Tool: Profile", OPTIONS, "");
    }

    private static String USAGE_FAIL(String msg) {
        USAGE();
        throw new IllegalArgumentException(msg);
    }

    private static class ProfileWorker implements Callable<Long> {

        private final ArrayBlockingQueue<FetchEmitTuple> queue;
        private final ExtractProfiler extractProfiler;
        private final AtomicInteger processed;

        ProfileWorker(ArrayBlockingQueue<FetchEmitTuple> queue, ExtractProfiler extractProfiler, AtomicInteger processed) {
            this.queue = queue;
            this.extractProfiler = extractProfiler;
            this.processed = processed;
        }

        @Override
        public Long call() throws Exception {
            while (true) {
                FetchEmitTuple t = queue.poll(1, TimeUnit.SECONDS);
                if (t == null) {
                    LOG.info("ExtractProfileWorker waiting on queue");
                    continue;
                }
                if (t == PipesIterator.COMPLETED_SEMAPHORE) {
                    LOG.debug("worker hit semaphore and is stopping");
                    extractProfiler.closeWriter();
                    //hangs
                    queue.put(PipesIterator.COMPLETED_SEMAPHORE);
                    return PROFILE_WORKER_COMPLETED_VALUE;
                }
                extractProfiler.processFileResource(t.getFetchKey());
                processed.incrementAndGet();
            }
        }
    }

    private static class ExtractProfilerBuilder {
        private final List<TableInfo> tableInfos;
        private final List<TableInfo> refTableInfos;

        public ExtractProfilerBuilder() {
            List<TableInfo> tableInfos = new ArrayList();
            tableInfos.add(ProfilerBase.MIME_TABLE);
            tableInfos.add(ExtractProfiler.CONTAINER_TABLE);
            tableInfos.add(ExtractProfiler.PROFILE_TABLE);
            tableInfos.add(ExtractProfiler.EXTRACT_EXCEPTION_TABLE);
            tableInfos.add(ExtractProfiler.EXCEPTION_TABLE);
            tableInfos.add(ExtractProfiler.CONTENTS_TABLE);
            tableInfos.add(ExtractProfiler.TAGS_TABLE);
            tableInfos.add(ExtractProfiler.EMBEDDED_FILE_PATH_TABLE);
            this.tableInfos = Collections.unmodifiableList(tableInfos);

            List<TableInfo> refTableInfos = new ArrayList<>();
            refTableInfos.add(ProfilerBase.REF_PARSE_ERROR_TYPES);
            refTableInfos.add(ProfilerBase.REF_PARSE_EXCEPTION_TYPES);
            refTableInfos.add(ProfilerBase.REF_EXTRACT_EXCEPTION_TYPES);
            this.refTableInfos = Collections.unmodifiableList(refTableInfos);
        }


        protected List<TableInfo> getRefTableInfos() {
            return refTableInfos;
        }

        protected List<TableInfo> getNonRefTableInfos() {
            return tableInfos;
        }

        protected TableInfo getMimeTable() {
            return ProfilerBase.MIME_TABLE;
        }

        public void populateRefTables(JDBCUtil dbUtil, MimeBuffer mimeBuffer) throws IOException, SQLException {
            boolean refTablesPopulated = true;
            try {
                Connection connection = dbUtil.getConnection();
                for (TableInfo tableInfo : getRefTableInfos()) {
                    int rows = 0;
                    try (ResultSet rs = connection
                            .createStatement()
                            .executeQuery("select * from " + tableInfo.getName())) {
                        while (rs.next()) {
                            rows++;
                        }
                    }
                    if (rows == 0) {
                        refTablesPopulated = false;
                        break;
                    }

                }
            } catch (SQLException e) {
                //swallow
            }
            if (refTablesPopulated) {
                LOG.info("ref tables are already populated");
                return;
            }

            IDBWriter writer = getDBWriter(getRefTableInfos(), dbUtil, mimeBuffer);
            Map<Cols, String> m = new HashMap<>();
            for (ProfilerBase.PARSE_ERROR_TYPE t : ProfilerBase.PARSE_ERROR_TYPE.values()) {
                m.clear();
                m.put(Cols.PARSE_ERROR_ID, Integer.toString(t.ordinal()));
                m.put(Cols.PARSE_ERROR_DESCRIPTION, t.name());
                writer.writeRow(ProfilerBase.REF_PARSE_ERROR_TYPES, m);
            }

            for (ProfilerBase.EXCEPTION_TYPE t : ProfilerBase.EXCEPTION_TYPE.values()) {
                m.clear();
                m.put(Cols.PARSE_EXCEPTION_ID, Integer.toString(t.ordinal()));
                m.put(Cols.PARSE_EXCEPTION_DESCRIPTION, t.name());
                writer.writeRow(ProfilerBase.REF_PARSE_EXCEPTION_TYPES, m);
            }

            for (ExtractReaderException.TYPE t : ExtractReaderException.TYPE.values()) {
                m.clear();
                m.put(Cols.EXTRACT_EXCEPTION_ID, Integer.toString(t.ordinal()));
                m.put(Cols.EXTRACT_EXCEPTION_DESCRIPTION, t.name());
                writer.writeRow(ProfilerBase.REF_EXTRACT_EXCEPTION_TYPES, m);
            }
            writer.close();
        }

        protected IDBWriter getDBWriter(List<TableInfo> tableInfos, JDBCUtil dbUtil, MimeBuffer mimeBuffer) throws IOException, SQLException {
            Connection conn = dbUtil.getConnection();
            return new DBWriter(conn, tableInfos, dbUtil, mimeBuffer);
        }
    }
}
