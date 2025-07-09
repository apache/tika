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

public class ExtractComparerRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ExtractComparerRunner.class);
    private static final long DIR_WALKER_COMPLETED_VALUE = 2;
    private static final long COMPARER_WORKER_COMPLETED_VALUE = 1;

    static Options OPTIONS;

    static {

        OPTIONS = new Options()
                .addOption(Option.builder("a").longOpt("extractsA").hasArg().desc("required: directory of 'A' extracts").build())
                .addOption(Option.builder("b").longOpt("extractsB").hasArg().desc("required: directory of 'B' extracts").build())
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
        Path extractsADir = commandLine.hasOption('a') ? Paths.get(commandLine.getOptionValue('a')) : Paths.get(USAGE_FAIL("Must specify extractsA dir: -a"));
        Path extractsBDir = commandLine.hasOption('b') ? Paths.get(commandLine.getOptionValue('b')) : Paths.get(USAGE_FAIL("Must specify extractsB dir: -b"));
        Path inputDir = commandLine.hasOption('i') ? Paths.get(commandLine.getOptionValue('i')) : extractsADir;
        String dbPath = commandLine.hasOption('d') ? commandLine.getOptionValue('d') : USAGE_FAIL("Must specify the db name: -d");
        String jdbcString = getJdbcConnectionString(dbPath);
        execute(inputDir, extractsADir, extractsBDir, jdbcString, evalConfig);
    }

    private static String getJdbcConnectionString(String dbPath) {
        if (dbPath.startsWith("jdbc:")) {
            return dbPath;
        }
        //default to h2
        Path p = Paths.get(dbPath);
        return "jdbc:h2:file:" + p.toAbsolutePath();

    }

    private static void execute(Path inputDir, Path extractsA, Path extractsB, String dbPath, EvalConfig evalConfig) throws SQLException, IOException {

        //parameterize this? if necesssary
        try {
            ProfilerBase.loadCommonTokens(null, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        JDBCUtil jdbcUtil = new JDBCUtil(dbPath, evalConfig.getJdbcDriverClass());
        ExtractComparerBuilder builder = new ExtractComparerBuilder();
        MimeBuffer mimeBuffer = initTables(jdbcUtil, builder, dbPath, evalConfig);
        builder.populateRefTables(jdbcUtil, mimeBuffer);

        AtomicInteger enqueued = new AtomicInteger(0);
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
            ExtractComparer extractComparer = new ExtractComparer(inputDir, extractsA, extractsB, extractReader,
                    builder.getDBWriter(builder.getNonRefTableInfos(), jdbcUtil, mimeBuffer));
            executorCompletionService.submit(new ComparerWorker(queue, extractComparer, processed));
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
                    } else if (result == COMPARER_WORKER_COMPLETED_VALUE) {
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

    private static MimeBuffer initTables(JDBCUtil jdbcUtil, ExtractComparerBuilder builder, String connectionString, EvalConfig evalConfig) throws SQLException, IOException {

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

    private static class ComparerWorker implements Callable<Long> {

        private final ArrayBlockingQueue<FetchEmitTuple> queue;
        private final ExtractComparer extractComparer;
        private final AtomicInteger processed;

        ComparerWorker(ArrayBlockingQueue<FetchEmitTuple> queue, ExtractComparer extractComparer, AtomicInteger processed) {
            this.queue = queue;
            this.extractComparer = extractComparer;
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
                    extractComparer.closeWriter();
                    //hangs
                    queue.put(PipesIterator.COMPLETED_SEMAPHORE);
                    return COMPARER_WORKER_COMPLETED_VALUE;
                }
                extractComparer.processFileResource(t.getFetchKey());
                processed.incrementAndGet();
            }
        }
    }

    private static class ExtractComparerBuilder {
        private final List<TableInfo> tableInfosA;
        private final List<TableInfo> tableInfosB;
        private final List<TableInfo> tableInfosAandB;
        private final List<TableInfo> refTableInfos;

        public ExtractComparerBuilder() {
            List<TableInfo> tableInfosA = new ArrayList<>();
            List<TableInfo> tableInfosB = new ArrayList<>();
            List<TableInfo> tableInfosAandB = new ArrayList<>();
            tableInfosA.add(ExtractComparer.PROFILES_A);
            tableInfosA.add(ExtractComparer.EXCEPTION_TABLE_A);
            tableInfosA.add(ExtractComparer.TAGS_TABLE_A);
            tableInfosA.add(ExtractComparer.CONTENTS_TABLE_A);
            tableInfosA.add(ExtractComparer.EXTRACT_EXCEPTION_TABLE_A);
            tableInfosA.add(ExtractComparer.EMBEDDED_FILE_PATH_TABLE_A);

            tableInfosB.add(ExtractComparer.PROFILES_B);
            tableInfosB.add(ExtractComparer.EXCEPTION_TABLE_B);
            tableInfosB.add(ExtractComparer.EXTRACT_EXCEPTION_TABLE_B);
            tableInfosB.add(ExtractComparer.TAGS_TABLE_B);
            tableInfosB.add(ExtractComparer.CONTENTS_TABLE_B);
            tableInfosB.add(ExtractComparer.EMBEDDED_FILE_PATH_TABLE_B);

            tableInfosAandB.add(ExtractComparer.COMPARISON_CONTAINERS);
            tableInfosAandB.add(ExtractComparer.CONTENT_COMPARISONS);
            tableInfosAandB.add(ProfilerBase.MIME_TABLE);

            List<TableInfo> refTableInfos = new ArrayList<>();
            refTableInfos.add(ExtractComparer.REF_PAIR_NAMES);
            refTableInfos.add(ProfilerBase.REF_PARSE_ERROR_TYPES);
            refTableInfos.add(ProfilerBase.REF_PARSE_EXCEPTION_TYPES);
            refTableInfos.add(ProfilerBase.REF_EXTRACT_EXCEPTION_TYPES);

            this.tableInfosA = Collections.unmodifiableList(tableInfosA);
            this.tableInfosB = Collections.unmodifiableList(tableInfosB);
            this.tableInfosAandB = Collections.unmodifiableList(tableInfosAandB);
            this.refTableInfos = Collections.unmodifiableList(refTableInfos);
        }


        protected List<TableInfo> getRefTableInfos() {
            return refTableInfos;
        }

        protected List<TableInfo> getNonRefTableInfos() {
            List<TableInfo> allNonRefTables = new ArrayList<>();
            allNonRefTables.addAll(tableInfosA);
            allNonRefTables.addAll(tableInfosB);
            allNonRefTables.addAll(tableInfosAandB);
            return Collections.unmodifiableList(allNonRefTables);
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
