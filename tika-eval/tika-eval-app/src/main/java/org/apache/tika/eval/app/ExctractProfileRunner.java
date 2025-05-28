package org.apache.tika.eval.app;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.eval.app.batch.FileResource;
import org.apache.tika.eval.app.batch.PathResource;
import org.apache.tika.eval.app.db.JDBCUtil;
import org.apache.tika.eval.app.db.MimeBuffer;
import org.apache.tika.eval.app.io.ExtractReader;
import org.apache.tika.eval.app.io.IDBWriter;

public class ExctractProfileRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ExctractProfileRunner.class);
    private static final PathResource SEMAPHORE = new PathResource(Paths.get("/"), "STOP");

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
        execute(inputDir, extractsDir, dbPath, evalConfig);
    }

    private static void execute(Path inputDir, Path extractsDir, String dbPath, EvalConfig evalConfig) {

        ArrayBlockingQueue<FileResource> queue = new ArrayBlockingQueue<>(1000);
        DirectoryWalker fileWalker = new DirectoryWalker(inputDir, queue);
        ExecutorService executorService = Executors.newFixedThreadPool(evalConfig.numThreads + 1);
        ExecutorCompletionService<Integer> executorCompletionService = new ExecutorCompletionService<>(executorService);
        executorCompletionService.submit(fileWalker);
        IDBWriter dbWriter = buildDBWriter();
        for (int i = 0; i < evalConfig.numThreads; i++) {
            ExtractReader extractReader = new ExtractReader(ExtractReader.ALTER_METADATA_LIST.AS_IS, evalConfig.minExtractLength, evalConfig.maxExtractLength);

            ExtractProfiler extractProfiler = new ExtractProfiler(inputDir, extractsDir, extractReader, dbWriter);
            executorCompletionService.submit(new ProfileWorker(queue, extractProfiler));
        }

        int finished = 0;
        try {
            while (finished < evalConfig.numThreads + 1) {
                //blocking
                Future<Integer> future = executorCompletionService.take();
                Integer result = future.get();
                if (result != null) {
                    finished++;
                }

            }
        } catch (InterruptedException e) {
            LOG.info("interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            executorService.shutdownNow();
        }

    }

    private static IDBWriter buildDBWriter(String connectionString, String driverClass) {
        MimeBuffer mimeBuffer = null;
        JDBCUtil dbUtil = new JDBCUtil(connectionString, driverClass);
        //Step 1. Used to be update table infos with prefixes
        updateTableInfosWithPrefixes(localAttrs);

        JDBCUtil.CREATE_TABLE createRegularTable = (forceDrop) ? JDBCUtil.CREATE_TABLE.DROP_IF_EXISTS : JDBCUtil.CREATE_TABLE.THROW_EX_IF_EXISTS;

        JDBCUtil.CREATE_TABLE createRefTable = (forceDrop) ? JDBCUtil.CREATE_TABLE.DROP_IF_EXISTS : JDBCUtil.CREATE_TABLE.SKIP_IF_EXISTS;

        //step 2. create the tables
        dbUtil.createTables(getNonRefTableInfos(), JDBCUtil.CREATE_TABLE.THROW_EX_IF_EXISTS);
        dbUtil.createTables(getRefTableInfos(), JDBCUtil.CREATE_TABLE.THROW_EX_IF_EXISTS);

        //step 3. create mime buffer
        this.mimeBuffer = new MimeBuffer(dbUtil.getConnection(), getMimeTable(), TikaConfig.getDefaultConfig());

        //step 4. populate the reference tables
        populateRefTables();

        return mimeBuffer;


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

    private static class ProfileWorker implements Callable<Integer> {

        private final ArrayBlockingQueue<FileResource> queue;
        private final ExtractProfiler extractProfiler;
        ProfileWorker(ArrayBlockingQueue<FileResource> queue, ExtractProfiler extractProfiler) {
            this.queue = queue;
            this.extractProfiler = extractProfiler;
        }

        @Override
        public Integer call() throws Exception {
            while (true) {
                FileResource resource = queue.poll(1, TimeUnit.SECONDS);
                if (resource == null) {
                    LOG.info("ExtractProfileWorker waiting on queue");
                    continue;
                }
                if (resource == SEMAPHORE) {
                    LOG.debug("worker hit semaphore and is stopping");
                    //hangs
                    queue.put(resource);
                    return 1;
                }
                extractProfiler.processFileResource(resource);
            }
        }
    }

    private static class DirectoryWalker implements Callable<Integer> {
        private final Path startDir;
        private final ArrayBlockingQueue<FileResource> queue;

        public DirectoryWalker(Path startDir, ArrayBlockingQueue<FileResource> queue) {
            this.startDir = startDir;
            this.queue = queue;
        }

        @Override
        public Integer call() throws Exception {
            Files.walkFileTree(startDir, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    //blocking
                    try {
                        queue.put(new PathResource(file, startDir.relativize(file).toString()));
                    } catch (InterruptedException e) {
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
            return 0;
        }
    }
}
