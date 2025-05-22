package org.apache.tika.eval.app;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import org.apache.tika.batch.FileResource;

public class FileProfileRunner {
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
        FileWalker fileWalker = new FileWalker(queue)
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
}
