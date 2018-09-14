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

package org.apache.tika.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TikaServerWatchDog {

    private enum CHILD_STATUS {
        INITIALIZING,
        RUNNING,
        SHUTTING_DOWN
    }

    private static final Logger LOG = LoggerFactory.getLogger(TikaServerWatchDog.class);

    private Object[] childStatusLock = new Object[0];
    private volatile CHILD_STATUS childStatus = CHILD_STATUS.INITIALIZING;
    private volatile Instant lastPing = null;
    private ChildProcess childProcess = null;
    int restarts = 0;

    public void execute(String[] args, ServerTimeouts serverTimeouts) throws Exception {
        //if the child thread is in stop-the-world mode, and isn't
        //responding to the ping, this thread checks to make sure
        //that the parent ping is sent and received often enough
        //If it isn't, this force destroys the child process.
        Thread pingTimer = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    long tmpLastPing = -1L;
                    synchronized (childStatusLock) {
                        if (childStatus == CHILD_STATUS.RUNNING) {
                            tmpLastPing = lastPing.toEpochMilli();
                        }
                    }
                    if (tmpLastPing > 0) {
                        long elapsed = Duration.between(Instant.ofEpochMilli(tmpLastPing), Instant.now()).toMillis();
                        if (elapsed > serverTimeouts.getPingTimeoutMillis()) {
                            Process processToDestroy = null;
                            try {
                                processToDestroy = childProcess.process;
                            } catch (NullPointerException e) {
                                //ignore
                            }
                            destroyChildForcibly(processToDestroy);
                        }
                    }
                    try {
                        Thread.sleep(serverTimeouts.getPingPulseMillis());
                    } catch (InterruptedException e) {
                        //swallow
                    }
                }
            }
        }
        );
        pingTimer.setDaemon(true);
        pingTimer.start();
        try {
            childProcess = new ChildProcess(args);
            setChildStatus(CHILD_STATUS.RUNNING);
            while (true) {

                if (!childProcess.ping()) {
                    setChildStatus(CHILD_STATUS.INITIALIZING);
                    lastPing = null;
                    childProcess.close();
                    LOG.info("About to restart the child process");
                    childProcess = new ChildProcess(args);
                    LOG.info("Successfully restarted child process -- {} restarts so far)", ++restarts);
                    setChildStatus(CHILD_STATUS.RUNNING);
                }
                Thread.sleep(serverTimeouts.getPingPulseMillis());
            }
        } catch (InterruptedException e) {
            //interrupted...shutting down
        } finally {
            setChildStatus(CHILD_STATUS.SHUTTING_DOWN);
            if (childProcess != null) {
                childProcess.close();
            }
        }
    }

    private void setChildStatus(CHILD_STATUS status) {
        synchronized (childStatusLock) {
            childStatus = status;
        }
    }

    private static List<String> extractArgs(String[] args) {
        List<String> argList = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-J") || args[i].equals("-spawnChild") || args[i].equals("--spawnChild")) {
                continue;
            }
            argList.add(args[i]);
        }
        return argList;
    }

    private static List<String> extractJVMArgs(String[] args) {
        List<String> jvmArgs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-J")) {
                jvmArgs.add("-"+args[i].substring(2));
            }
        }
        return jvmArgs;
    }

    private class ChildProcess {
        private Thread SHUTDOWN_HOOK = null;

        Process process;
        DataInputStream fromChild;
        DataOutputStream toChild;



        private ChildProcess(String[] args) throws Exception {
            this.process = startProcess(args);

            this.fromChild = new DataInputStream(process.getInputStream());
            this.toChild = new DataOutputStream(process.getOutputStream());
            byte status = fromChild.readByte();
            if (status != ServerStatus.STATUS.OPERATING.getByte()) {
                throw new IOException("bad status from child process: "+
                        ServerStatus.STATUS.lookup(status));
            }
            lastPing = Instant.now();
        }

        public boolean ping() {
            lastPing = Instant.now();
            try {
                toChild.writeByte(ServerStatus.DIRECTIVES.PING.getByte());
                toChild.flush();
            } catch (Exception e) {
                LOG.warn("Exception pinging child process", e);
                return false;
            }
            try {
                byte status = fromChild.readByte();
                if (status != ServerStatus.STATUS.OPERATING.getByte()) {
                    LOG.warn("Received status from child: {}",
                            ServerStatus.STATUS.lookup(status));
                    return false;
                }
            } catch (Exception e) {
                LOG.warn("Exception receiving status from child", e);
                return false;
            }
            return true;
        }

        private void close() {
            try {
                toChild.writeByte(ServerStatus.DIRECTIVES.SHUTDOWN.getByte());
                toChild.flush();
            } catch (Exception e) {
                LOG.warn("Exception asking child to shutdown", e);
            }
            //TODO: add a gracefully timed shutdown routine
            try {
                fromChild.close();
            } catch (Exception e) {
                LOG.warn("Problem shutting down reader from child", e);
            }

            try {
                toChild.close();
            } catch (Exception e) {
                LOG.warn("Problem shutting down writer to child", e);
            }
            destroyChildForcibly(process);
        }

        private Process startProcess(String[] args) throws IOException {
            ProcessBuilder builder = new ProcessBuilder();
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            List<String> argList = new ArrayList<>();
            List<String> jvmArgs = extractJVMArgs(args);
            List<String> childArgs = extractArgs(args);
            argList.add("java");
            if (! jvmArgs.contains("-cp") && ! jvmArgs.contains("--classpath")) {
                String cp = System.getProperty("java.class.path");
                jvmArgs.add("-cp");
                jvmArgs.add(cp);
            }
            argList.addAll(jvmArgs);
            argList.add("org.apache.tika.server.TikaServerCli");
            argList.addAll(childArgs);
            argList.add("-child");

            builder.command(argList);
            Process process = builder.start();
            if (SHUTDOWN_HOOK != null) {
                Runtime.getRuntime().removeShutdownHook(SHUTDOWN_HOOK);
            }
            SHUTDOWN_HOOK = new Thread(() -> process.destroyForcibly());
            Runtime.getRuntime().addShutdownHook(SHUTDOWN_HOOK);

            return process;
        }
    }

    private static synchronized void destroyChildForcibly(Process process) {
        process = process.destroyForcibly();
        try {
            boolean destroyed = process.waitFor(60, TimeUnit.SECONDS);
            if (! destroyed) {
                LOG.error("Child process still alive after 60 seconds. " +
                        "Shutting down the parent.");
                System.exit(1);
            }

        } catch (InterruptedException e) {
            //swallow
        }
    }

}
