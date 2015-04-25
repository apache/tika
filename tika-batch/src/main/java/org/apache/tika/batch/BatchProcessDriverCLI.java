package org.apache.tika.batch;
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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.tika.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchProcessDriverCLI {

    /**
     * This relies on an special exit values of 254 (do not restart),
     * 0 ended correctly, 253 ended with exception (do restart)
     */
    public static final int PROCESS_RESTART_EXIT_CODE = 253;
    //make sure this is above 255 to avoid stopping on system errors
    //that is, if there is a system error (e.g. 143), you
    //should restart the process.
    public static final int PROCESS_NO_RESTART_EXIT_CODE = 254;
    public static final int PROCESS_COMPLETED_SUCCESSFULLY = 0;
    private static Logger logger = LoggerFactory.getLogger(BatchProcessDriverCLI.class);

    private int maxProcessRestarts = -1;
    private long pulseMillis = 1000;

    //how many times to wait pulseMillis milliseconds if a restart
    //message has been received through stdout, but the
    //child process has not yet exited
    private int waitNumLoopsAfterRestartmessage = 60;


    private volatile boolean userInterrupted = false;
    private boolean receivedRestartMsg = false;
    private Process process = null;

    private StreamGobbler errorWatcher = null;
    private StreamGobbler outGobbler = null;
    private InterruptWriter interruptWriter = null;
    private final InterruptWatcher interruptWatcher =
            new InterruptWatcher(System.in);

    private Thread errorWatcherThread = null;
    private Thread outGobblerThread = null;
    private Thread interruptWriterThread = null;
    private final Thread interruptWatcherThread = new Thread(interruptWatcher);

    private final String[] commandLine;
    private int numRestarts = 0;
    private boolean redirectChildProcessToStdOut = true;

    public BatchProcessDriverCLI(String[] commandLine){
        this.commandLine = tryToReadMaxRestarts(commandLine);
    }

    private String[] tryToReadMaxRestarts(String[] commandLine) {
        List<String> args = new ArrayList<String>();
        for (int i = 0; i < commandLine.length; i++) {
            String arg = commandLine[i];
            if (arg.equals("-maxRestarts")) {
                if (i == commandLine.length-1) {
                    throw new IllegalArgumentException("Must specify an integer after \"-maxRestarts\"");
                }
                String restartNumString = commandLine[i+1];
                try {
                    maxProcessRestarts = Integer.parseInt(restartNumString);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Must specify an integer after \"-maxRestarts\" arg.");
                }
                i++;
            } else {
                args.add(arg);
            }
        }
        return args.toArray(new String[args.size()]);
    }

    public void execute() throws Exception {

        interruptWatcherThread.setDaemon(true);
        interruptWatcherThread.start();
        logger.info("about to start driver");
        start();
        int loopsAfterRestartMessageReceived = 0;
        while (!userInterrupted) {
            Integer exit = null;
            try {
                logger.trace("about to check exit value");
                exit = process.exitValue();
                logger.info("The child process has finished with an exit value of: "+exit);
                stop();
            } catch (IllegalThreadStateException e) {
                //hasn't exited
                logger.trace("process has not exited; IllegalThreadStateException");
            }

            logger.trace("Before sleep:" +
                        " exit=" + exit + " receivedRestartMsg=" + receivedRestartMsg);

            //Even if the process has exited,
            //wait just a little bit to make sure that
            //mustRestart hasn't been set to true
            try {
                Thread.sleep(pulseMillis);
            } catch (InterruptedException e) {
                logger.trace("interrupted exception during sleep");
            }
            logger.trace("After sleep:" +
                    " exit=" + exit + " receivedRestartMsg=" + receivedRestartMsg);
            //if we've gotten the message via stdout to restart
            //but the process hasn't exited yet, give it another
            //chance
            if (receivedRestartMsg && exit == null) {
                loopsAfterRestartMessageReceived++;
                logger.warn("Must restart, still not exited; loops after restart: " +
                            loopsAfterRestartMessageReceived);
                continue;
            }
            if (loopsAfterRestartMessageReceived > waitNumLoopsAfterRestartmessage) {
                logger.trace("About to try to restart because:" +
                        " exit=" + exit + " receivedRestartMsg=" + receivedRestartMsg);
                logger.warn("Restarting after exceeded wait loops waiting for exit: "+
                        loopsAfterRestartMessageReceived);
                boolean restarted = restart(exit, receivedRestartMsg);
                if (!restarted) {
                    break;
                }
            } else if (exit != null && exit != BatchProcessDriverCLI.PROCESS_NO_RESTART_EXIT_CODE
                    && exit != BatchProcessDriverCLI.PROCESS_COMPLETED_SUCCESSFULLY) {
                logger.trace("About to try to restart because:" +
                            " exit=" + exit + " receivedRestartMsg=" + receivedRestartMsg);

                if (exit == BatchProcessDriverCLI.PROCESS_RESTART_EXIT_CODE) {
                    logger.info("Restarting on expected restart code");
                } else {
                    logger.warn("Restarting on unexpected restart code: "+exit);
                }
                boolean restarted = restart(exit, receivedRestartMsg);
                if (!restarted) {
                    break;
                }
            } else if (exit != null && (exit == PROCESS_COMPLETED_SUCCESSFULLY
                    || exit == BatchProcessDriverCLI.PROCESS_NO_RESTART_EXIT_CODE)) {
                logger.trace("Will not restart: "+exit);
                break;
            }
        }
        logger.trace("about to call shutdown driver now");
        shutdownDriverNow();
        logger.info("Process driver has completed");
    }

    private void shutdownDriverNow() {
        if (process != null) {
            for (int i = 0; i < 60; i++) {

                logger.trace("trying to shut down: "+i);
                try {
                    int exit = process.exitValue();
                    logger.trace("trying to stop:"+exit);
                    stop();
                    interruptWatcherThread.interrupt();
                    return;
                } catch (IllegalThreadStateException e) {
                    //hasn't exited
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    //swallow
                }
            }
            logger.error("Process didn't stop after 60 seconds after shutdown. " +
                    "I am forcefully killing it.");
        }
        interruptWatcherThread.interrupt();
    }

    public int getNumRestarts() {
        return numRestarts;
    }

    public boolean getUserInterrupted() {
        return userInterrupted;
    }

    /**
     * Tries to restart (stop and then start) the child process
     * @return whether or not this was successful, will be false if numRestarts >= maxProcessRestarts
     * @throws Exception
     */
    private boolean restart(Integer exitValue, boolean receivedRestartMsg) throws Exception {
        if (maxProcessRestarts > -1 && numRestarts >= maxProcessRestarts) {
            logger.warn("Hit the maximum number of process restarts. Driver is shutting down now.");
            stop();
            return false;
        }
        logger.warn("Must restart process (exitValue="+exitValue+" numRestarts="+numRestarts+
                " receivedRestartMessage="+receivedRestartMsg+")");
        stop();
        start();
        numRestarts++;
        return true;
    }

    private void stop() {
        if (process != null) {
            logger.trace("destroying a non-null process");
            process.destroy();
        }

        receivedRestartMsg = false;
        //interrupt the writer thread first
        interruptWriterThread.interrupt();

        errorWatcher.stopGobblingAndDie();
        outGobbler.stopGobblingAndDie();
        errorWatcherThread.interrupt();
        outGobblerThread.interrupt();
    }

    private void start() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(commandLine);
        builder.directory(new File("."));
        process = builder.start();

        errorWatcher = new StreamWatcher(process.getErrorStream());
        errorWatcherThread = new Thread(errorWatcher);
        errorWatcherThread.start();

        outGobbler = new StreamGobbler(process.getInputStream());
        outGobblerThread = new Thread(outGobbler);
        outGobblerThread.start();

        interruptWriter = new InterruptWriter(process.getOutputStream());
        interruptWriterThread = new Thread(interruptWriter);
        interruptWriterThread.start();

    }

    /**
     * Typically only used for testing.  This determines whether or not
     * to redirect child process's stdOut to driver's stdout
     * @param redirectChildProcessToStdOut should the driver redirect the child's stdout
     */
    public void setRedirectChildProcessToStdOut(boolean redirectChildProcessToStdOut) {
        this.redirectChildProcessToStdOut = redirectChildProcessToStdOut;
    }

    /**
     * Class to watch stdin from the driver for anything that is typed.
     * This will currently cause an interrupt if anything followed by
     * a return key is entered.  We may want to add an "Are you sure?" dialogue.
     */
    private class InterruptWatcher implements Runnable {
        private BufferedReader reader;

        private InterruptWatcher(InputStream is) {
            reader = new BufferedReader(new InputStreamReader(is, IOUtils.UTF_8));
        }

        @Override
        public void run() {
            try {
                //this will block.
                //as soon as it reads anything,
                //set userInterrupted to true and stop
                reader.readLine();
                userInterrupted = true;
            } catch (IOException e) {
                //swallow
            }
        }
    }

    /**
     * Class that writes to the child process
     * to force an interrupt in the child process.
     */
    private class InterruptWriter implements Runnable {
        private final Writer writer;

        private InterruptWriter(OutputStream os) {
            this.writer = new OutputStreamWriter(os, IOUtils.UTF_8);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Thread.sleep(500);
                    if (userInterrupted) {
                        writer.write(String.format(Locale.ENGLISH, "Ave atque vale!%n"));
                        writer.flush();
                    }
                }
            } catch (IOException e) {
                //swallow
            } catch (InterruptedException e) {
                //job is done, ok
            }
        }
    }

    private class StreamGobbler implements Runnable {
        //plagiarized from org.apache.oodt's StreamGobbler
        protected final BufferedReader reader;
        protected boolean running = true;

        private StreamGobbler(InputStream is) {
            this.reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(is),
                    IOUtils.UTF_8));
        }

        @Override
        public void run() {
            String line = null;
            try {
                logger.trace("gobbler starting to read");
                while ((line = reader.readLine()) != null && this.running) {
                    if (redirectChildProcessToStdOut) {
                        System.out.println("BatchProcess:"+line);
                    }
                }
            } catch (IOException e) {
                logger.trace("gobbler io exception");
                //swallow ioe
            }
            logger.trace("gobbler done");
        }

        private void stopGobblingAndDie() {
            logger.trace("stop gobbling");
            running = false;
            IOUtils.closeQuietly(reader);
        }
    }

    private class StreamWatcher extends StreamGobbler implements Runnable {
        //plagiarized from org.apache.oodt's StreamGobbler

        private StreamWatcher(InputStream is){
            super(is);
        }

        @Override
        public void run() {
            String line = null;
            try {
                logger.trace("watcher starting to read");
                while ((line = reader.readLine()) != null && this.running) {
                    if (line.startsWith(BatchProcess.BATCH_CONSTANTS.BATCH_PROCESS_FATAL_MUST_RESTART.toString())) {
                        receivedRestartMsg = true;
                    }
                    logger.info("BatchProcess: "+line);
                }
            } catch (IOException e) {
                logger.trace("watcher io exception");
                //swallow ioe
            }
            logger.trace("watcher done");
        }
    }


    public static void main(String[] args) throws Exception {

        BatchProcessDriverCLI runner = new BatchProcessDriverCLI(args);
        runner.execute();
        System.out.println("FSBatchProcessDriver has gracefully completed");
        System.exit(0);
    }
}
