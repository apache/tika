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

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;


/**
 * This is a base class for file consumers. The
 * goal of this class is to abstract out the multithreading
 * and recordkeeping components.
 * <p/>
 */
public abstract class FileResourceConsumer implements Callable<IFileProcessorFutureResult> {

    private enum STATE {
        NOT_YET_STARTED,
        ACTIVELY_CONSUMING,
        SWALLOWED_POISON,
        THREAD_INTERRUPTED,
        EXCEEDED_MAX_CONSEC_WAIT_MILLIS,
        ASKED_TO_SHUTDOWN,
        TIMED_OUT,
        CONSUMER_EXCEPTION,
        CONSUMER_ERROR,
        COMPLETED
    }

    public static String TIMED_OUT = "timed_out";
    public static String OOM = "oom";
    public static String IO_IS = "io_on_inputstream";
    public static String IO_OS = "io_on_outputstream";
    public static String PARSE_ERR = "parse_err";
    public static String PARSE_EX = "parse_ex";

    public static String ELAPSED_MILLIS = "elapsedMS";

    private static AtomicInteger numConsumers = new AtomicInteger(-1);
    protected static Logger logger = LoggerFactory.getLogger(FileResourceConsumer.class);

    private long maxConsecWaitInMillis = 10*60*1000;// 10 minutes

    private final ArrayBlockingQueue<FileResource> fileQueue;

    private final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();
    private final int consumerId;

    //used to lock checks on state to prevent
    private final Object lock = new Object();

    //this records the file that is currently
    //being processed.  It is null if no file is currently being processed.
    //no need for volatile because of lock for checkForStales
    private FileStarted currentFile = null;

    //total number of files consumed; volatile so that reporter
    //sees the latest
    private volatile int numResourcesConsumed = 0;

    //total number of exceptions that were handled by subclasses;
    //volatile so that reporter sees the latest
    private volatile int numHandledExceptions = 0;

    //after this has been set to ACTIVELY_CONSUMING,
    //this should only be set by setEndedState.
    private volatile STATE currentState = STATE.NOT_YET_STARTED;

    public FileResourceConsumer(ArrayBlockingQueue<FileResource> fileQueue) {
        this.fileQueue = fileQueue;
        consumerId = numConsumers.incrementAndGet();
    }

    public IFileProcessorFutureResult call() {
        currentState = STATE.ACTIVELY_CONSUMING;

        try {
            FileResource fileResource = getNextFileResource();
            while (fileResource != null) {
                logger.debug("file consumer is about to process: " + fileResource.getResourceId());
                boolean consumed = _processFileResource(fileResource);
                logger.debug("file consumer has finished processing: " + fileResource.getResourceId());

                if (consumed) {
                    numResourcesConsumed++;
                }
                fileResource = getNextFileResource();
            }
        } catch (InterruptedException e) {
            setEndedState(STATE.THREAD_INTERRUPTED);
        }

        setEndedState(STATE.COMPLETED);
        return new FileConsumerFutureResult(currentFile, numResourcesConsumed);
    }


    /**
     * Main piece of code that needs to be implemented.  Clients
     * are responsible for closing streams and handling the exceptions
     * that they'd like to handle.
     * <p/>
     * Unchecked throwables can be thrown past this, of course.  When an unchecked
     * throwable is thrown, this logs the error, and then rethrows the exception.
     * Clients/subclasses should make sure to catch and handle everything they can.
     * <p/>
     * The design goal is that the whole process should close up and shutdown soon after
     * an unchecked exception or error is thrown.
     * <p/>
     * Make sure to call {@link #incrementHandledExceptions()} appropriately in
     * your implementation of this method.
     * <p/>
     *
     * @param fileResource resource to process
     * @return whether or not a file was successfully processed
     */
    public abstract boolean processFileResource(FileResource fileResource);


    /**
     * Make sure to call this appropriately!
     */
    protected void incrementHandledExceptions() {
        numHandledExceptions++;
    }


    /**
     * Returns whether or not the consumer is still could process
     * a file or is still processing a file (ACTIVELY_CONSUMING or ASKED_TO_SHUTDOWN)
     * @return whether this consumer is still active
     */
    public boolean isStillActive() {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        } else if( currentState == STATE.NOT_YET_STARTED ||
                currentState == STATE.ACTIVELY_CONSUMING ||
                currentState == STATE.ASKED_TO_SHUTDOWN) {
            return true;
        }
        return false;
    }

    private boolean _processFileResource(FileResource fileResource) {
        currentFile = new FileStarted(fileResource.getResourceId());
        boolean consumed = false;
        try {
            consumed = processFileResource(fileResource);
        } catch (RuntimeException e) {
            setEndedState(STATE.CONSUMER_EXCEPTION);
            throw e;
        } catch (Error e) {
            setEndedState(STATE.CONSUMER_ERROR);
            throw e;
        }
        //if anything is thrown from processFileResource, then the fileStarted
        //will remain what it was right before the exception was thrown.
        currentFile = null;
        return consumed;
    }

    /**
     * This politely asks the consumer to shutdown.
     * Before processing another file, the consumer will check to see
     * if it has been asked to terminate.
     * <p>
     * This offers another method for politely requesting
     * that a FileResourceConsumer stop processing
     * besides passing it {@link org.apache.tika.batch.PoisonFileResource}.
     *
     */
    public void pleaseShutdown() {
        setEndedState(STATE.ASKED_TO_SHUTDOWN);
    }

    /**
     * Returns the name and start time of a file that is currently being processed.
     * If no file is currently being processed, this will return null.
     *
     * @return FileStarted or null
     */
    public FileStarted getCurrentFile() {
        return currentFile;
    }

    public int getNumResourcesConsumed() {
        return numResourcesConsumed;
    }

    public int getNumHandledExceptions() {
        return numHandledExceptions;
    }

    /**
     * Checks to see if the currentFile being processed (if there is one)
     * should be timed out (still being worked on after staleThresholdMillis).
     * <p>
     * If the consumer should be timed out, this will return the currentFile and
     * set the state to TIMED_OUT.
     * <p>
     * If the consumer was already timed out earlier or
     * is not processing a file or has been working on a file
     * for less than #staleThresholdMillis, then this will return null.
     * <p>
     * @param staleThresholdMillis threshold to determine whether the consumer has gone stale.
     * @return null or the file started that triggered the stale condition
     */
    public FileStarted checkForTimedOutMillis(long staleThresholdMillis) {
        //if there isn't a current file, don't bother obtaining lock
        if (currentFile == null) {
            return null;
        }
        //if threshold is < 0, don't even look.
        if (staleThresholdMillis < 0) {
            return null;
        }
        synchronized(lock) {
            //check again once the lock has been obtained
            if (currentState != STATE.ACTIVELY_CONSUMING
                    && currentState != STATE.ASKED_TO_SHUTDOWN) {
                return null;
            }
            FileStarted tmp = currentFile;
            if (tmp == null) {
                return null;
            }
            if (tmp.getElapsedMillis() > staleThresholdMillis) {
                setEndedState(STATE.TIMED_OUT);
                logger.error("{}", getXMLifiedLogMsg(
                        TIMED_OUT,
                        tmp.getResourceId(),
                        ELAPSED_MILLIS, Long.toString(tmp.getElapsedMillis())));
                return tmp;
            }
        }
        return null;
    }

    protected String getXMLifiedLogMsg(String type, String resourceId, String... attrs) {
        return getXMLifiedLogMsg(type, resourceId, null, attrs);
    }

    /**
     * Use this for structured output that captures resourceId and other attributes.
     *
     * @param type entity name for exception
     * @param resourceId resourceId string
     * @param t throwable can be null
     * @param attrs (array of key0, value0, key1, value1, etc.)
     */
    protected String getXMLifiedLogMsg(String type, String resourceId, Throwable t, String... attrs) {

        StringWriter writer = new StringWriter();
        try {
            XMLStreamWriter xml = xmlOutputFactory.createXMLStreamWriter(writer);
            xml.writeStartDocument();
            xml.writeStartElement(type);
            xml.writeAttribute("resourceId", resourceId);
            if (attrs != null) {
                //this assumes args has name value pairs alternating, name0 at 0, val0 at 1, name1 at 2, val2 at 3, etc.
                for (int i = 0; i < attrs.length - 1; i++) {
                    xml.writeAttribute(attrs[i], attrs[i + 1]);
                }
            }
            if (t != null) {
                StringWriter stackWriter = new StringWriter();
                PrintWriter printWriter = new PrintWriter(stackWriter);
                t.printStackTrace(printWriter);
                printWriter.flush();
                stackWriter.flush();
                xml.writeCharacters(stackWriter.toString());
            }
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
            xml.close();
        } catch (XMLStreamException e) {
            logger.error("error writing xml stream for: " + resourceId, t);
        }
        return writer.toString();
    }

    private FileResource getNextFileResource() throws InterruptedException {
        FileResource fileResource = null;
        long start = new Date().getTime();
        while (fileResource == null) {
            //check to see if thread is interrupted before polling
            if (Thread.currentThread().isInterrupted()) {
                setEndedState(STATE.THREAD_INTERRUPTED);
                logger.debug("Consumer thread was interrupted.");
                break;
            }

            synchronized(lock) {
                //need to lock here to prevent race condition with other threads setting state
                if (currentState != STATE.ACTIVELY_CONSUMING) {
                    logger.debug("Consumer already closed because of: "+ currentState.toString());
                    break;
                }
            }
            fileResource = fileQueue.poll(1L, TimeUnit.SECONDS);
            if (fileResource != null) {
                if (fileResource instanceof PoisonFileResource) {
                    setEndedState(STATE.SWALLOWED_POISON);
                    fileResource = null;
                }
                break;
            }
            logger.debug(consumerId + " is waiting for file and the queue size is: " + fileQueue.size());

            long elapsed = new Date().getTime() - start;
            if (maxConsecWaitInMillis > 0 && elapsed > maxConsecWaitInMillis) {
                setEndedState(STATE.EXCEEDED_MAX_CONSEC_WAIT_MILLIS);
                break;
            }
        }
        return fileResource;
    }

    protected void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e){
                logger.error(e.getMessage());
            }
        }
        closeable = null;
    }

    protected void flushAndClose(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        if (closeable instanceof Flushable){
            try {
                ((Flushable)closeable).flush();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
        close(closeable);
    }

    //do not overwrite a finished state except if
    //not yet started, actively consuming or shutting down.  This should
    //represent the initial cause; all subsequent calls
    //to set will be ignored!!!
    private void setEndedState(STATE cause) {
        synchronized(lock) {
            if (currentState == STATE.NOT_YET_STARTED ||
                    currentState == STATE.ACTIVELY_CONSUMING ||
                    currentState == STATE.ASKED_TO_SHUTDOWN) {
                currentState = cause;
            }
        }
    }

    /**
     * Utility method to handle logging equivalently among all
     * implementing classes.  Use, override or avoid as desired.
     *
     * @param resourceId resourceId
     * @param parser parser to use
     * @param is inputStream (will be closed by this method!)
     * @param handler handler for the content
     * @param m metadata
     * @param parseContext parse context
     * @throws Throwable (logs and then throws whatever was thrown (if anything)
     */
    protected void parse(final String resourceId, final Parser parser, InputStream is,
                         final ContentHandler handler,
                         final Metadata m, final ParseContext parseContext) throws Throwable {

        try {
            parser.parse(is, handler, m, parseContext);
        } catch (Throwable t) {
            if (t instanceof OutOfMemoryError) {
                logger.error(getXMLifiedLogMsg(OOM,
                        resourceId, t));
            } else if (t instanceof Error) {
                logger.error(getXMLifiedLogMsg(PARSE_ERR,
                        resourceId, t));
            } else {
                logger.warn(getXMLifiedLogMsg(PARSE_EX,
                        resourceId, t));
                incrementHandledExceptions();
            }
            throw t;
        } finally {
            close(is);
        }
    }

}
