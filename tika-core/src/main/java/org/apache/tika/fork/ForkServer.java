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
package org.apache.tika.fork;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParserFactory;
import org.xml.sax.SAXException;

class ForkServer implements Runnable {

    public static final byte ERROR = -1;

    public static final byte DONE = 0;

    public static final byte CALL = 1;

    public static final byte PING = 2;

    public static final byte RESOURCE = 3;

    public static final byte READY = 4;

    public static final byte FAILED_TO_START = 5;

    public static final byte INIT_PARSER_FACTORY_FACTORY = 6;
    public static final byte INIT_LOADER_PARSER = 7;
    public static final byte INIT_PARSER_FACTORY_FACTORY_LOADER = 8;
    private final Object[] lock = new Object[0];

    /** Input stream for reading from the parent process */
    private final DataInputStream input;

    /** Output stream for writing to the parent process */
    private final DataOutputStream output;

    private final boolean active = true;
    // milliseconds to sleep before checking to see if there has been any reading/writing
    // If no reading or writing in this time, shutdown the server.
    private long serverPulseMillis = 5000;
    private long serverParserTimeoutMillis = 60000;
    private long serverWaitTimeoutMillis = 60000;
    // can't be class Parser because then you'd
    // have to include that in bootstrap jar (legacy mode)
    private Object parser;
    private ClassLoader classLoader;
    private boolean parsing = false;
    private long since;

    /**
     * Sets up a forked server instance using the given stdin/out communication channel.
     *
     * @param input input stream for reading from the parent process
     * @param output output stream for writing to the parent process
     * @throws IOException if the server instance could not be created
     */
    public ForkServer(
            InputStream input,
            OutputStream output,
            long serverPulseMillis,
            long serverParserTimeoutMillis,
            long serverWaitTimeoutMillis)
            throws IOException {
        this.input = new DataInputStream(input);
        this.output = new DataOutputStream(output);
        this.serverPulseMillis = serverPulseMillis;
        this.serverParserTimeoutMillis = serverParserTimeoutMillis;
        this.serverWaitTimeoutMillis = serverWaitTimeoutMillis;
        this.parsing = false;
        this.since = System.currentTimeMillis();
    }

    /**
     * Starts a forked server process using the standard input and output streams for communication
     * with the parent process. Any attempts by stray code to read from standard input or write to
     * standard output is redirected to avoid interfering with the communication channel.
     *
     * @param args command line arguments, ignored
     * @throws Exception if the server could not be started
     */
    public static void main(String[] args) throws Exception {
        long serverPulseMillis = Long.parseLong(args[0]);
        long serverParseTimeoutMillis = Long.parseLong(args[1]);
        long serverWaitTimeoutMillis = Long.parseLong(args[2]);

        URL.setURLStreamHandlerFactory(new MemoryURLStreamHandlerFactory());

        ForkServer server =
                new ForkServer(
                        System.in,
                        System.out,
                        serverPulseMillis,
                        serverParseTimeoutMillis,
                        serverWaitTimeoutMillis);
        System.setIn(new ByteArrayInputStream(new byte[0]));
        System.setOut(System.err);

        Thread watchdog = new Thread(server, "Tika Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        server.processRequests();
    }

    public void run() {
        try {
            while (true) {
                synchronized (lock) {
                    long elapsed = System.currentTimeMillis() - since;
                    if (parsing && elapsed > serverParserTimeoutMillis) {
                        break;
                    } else if (!parsing
                            && serverWaitTimeoutMillis > 0
                            && elapsed > serverWaitTimeoutMillis) {
                        break;
                    }
                }
                Thread.sleep(serverPulseMillis);
            }
            System.exit(0);
        } catch (InterruptedException e) {
            // swallow
        }
    }

    public void processRequests() {
        // initialize
        try {
            initializeParserAndLoader();
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.flush();
            try {
                output.writeByte(FAILED_TO_START);
                output.flush();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.flush();
            }
            return;
        }
        // main loop
        try {
            while (true) {
                int request = input.read();
                if (request == -1) {
                    break;
                } else if (request == PING) {
                    output.writeByte(PING);
                } else if (request == CALL) {
                    call(classLoader, parser);
                } else {
                    throw new IllegalStateException("Unexpected request");
                }
                output.flush();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.err.flush();
    }

    private void initializeParserAndLoader()
            throws IOException, ClassNotFoundException, TikaException, SAXException {
        output.writeByte(READY);
        output.flush();

        int configIndex = input.read();
        if (configIndex == -1) {
            throw new TikaException("eof! pipe closed?!");
        }

        Object firstObject = readObject(ForkServer.class.getClassLoader());
        switch (configIndex) {
            case INIT_PARSER_FACTORY_FACTORY:
                if (firstObject instanceof ParserFactoryFactory) {
                    // the user has submitted a parser factory, but no class loader
                    classLoader = ForkServer.class.getClassLoader();
                    ParserFactory parserFactory = ((ParserFactoryFactory) firstObject).build();
                    parser = parserFactory.build();
                } else {
                    throw new IllegalArgumentException(
                            "Expecting only one object of class ParserFactoryFactory");
                }
                break;
            case INIT_LOADER_PARSER:
                if (firstObject instanceof ClassLoader) {
                    classLoader = (ClassLoader) firstObject;
                    Thread.currentThread().setContextClassLoader(classLoader);
                    // parser from parent process
                    parser = readObject(classLoader);
                } else {
                    throw new IllegalArgumentException(
                            "Expecting ClassLoader followed by a Parser");
                }
                break;
            case INIT_PARSER_FACTORY_FACTORY_LOADER:
                if (firstObject instanceof ParserFactoryFactory) {
                    // the user has submitted a parser factory and a class loader
                    ParserFactory parserFactory = ((ParserFactoryFactory) firstObject).build();
                    parser = parserFactory.build();
                    classLoader = (ClassLoader) readObject(ForkServer.class.getClassLoader());
                    Thread.currentThread().setContextClassLoader(classLoader);
                } else {
                    throw new IllegalStateException(
                            "Expecing ParserFactoryFactory followed by a class loader");
                }
                break;
        }
        output.writeByte(READY);
        output.flush();
    }

    private void call(ClassLoader loader, Object object) throws Exception {
        synchronized (lock) {
            parsing = true;
            since = System.currentTimeMillis();
        }
        try {
            Method method = getMethod(object, input.readUTF());
            Object[] args = new Object[method.getParameterTypes().length];
            for (int i = 0; i < args.length; i++) {
                args[i] = readObject(loader);
            }
            try {
                method.invoke(object, args);
                output.write(DONE);
            } catch (InvocationTargetException e) {
                output.write(ERROR);
                // Try to send the underlying Exception itself
                Throwable toSend = e.getCause();
                try {
                    ForkObjectInputStream.sendObject(toSend, output);
                } catch (NotSerializableException nse) {
                    // Need to build a serializable version of it
                    TikaException te = new TikaException(toSend.getMessage());
                    te.setStackTrace(toSend.getStackTrace());
                    ForkObjectInputStream.sendObject(te, output);
                }
            }
        } finally {
            synchronized (lock) {
                parsing = false;
                since = System.currentTimeMillis();
            }
        }
    }

    private Method getMethod(Object object, String name) {
        Class<?> klass = object.getClass();
        while (klass != null) {
            for (Class<?> iface : klass.getInterfaces()) {
                for (Method method : iface.getMethods()) {
                    if (name.equals(method.getName())) {
                        return method;
                    }
                }
            }
            klass = klass.getSuperclass();
        }
        return null;
    }

    /**
     * Deserializes an object from the given stream. The serialized object is expected to be
     * preceded by a size integer, that is used for reading the entire serialization into a memory
     * before deserializing it.
     *
     * @param loader class loader to be used for loading referenced classes
     * @throws IOException if the object could not be deserialized
     * @throws ClassNotFoundException if a referenced class is not found
     */
    private Object readObject(ClassLoader loader) throws IOException, ClassNotFoundException {
        Object object = ForkObjectInputStream.readObject(input, loader);
        if (object instanceof ForkProxy) {
            ((ForkProxy) object).init(input, output);
        }

        // Tell the parent process that we successfully received this object
        output.writeByte(ForkServer.DONE);
        output.flush();

        return object;
    }
}
