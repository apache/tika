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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.utils.ProcessUtils;

class ForkClient {
    private static final AtomicInteger CLIENT_COUNTER = new AtomicInteger(0);

    private final List<ForkResource> resources = new ArrayList<>();

    private final ClassLoader loader;

    private final File jar;

    private final Process process;

    private final DataOutputStream output;

    private final DataInputStream input;

    //this is used for debugging/smoke testing
    private final int id = CLIENT_COUNTER.incrementAndGet();

    private volatile int filesProcessed = 0;

    public ForkClient(Path tikaDir, ParserFactoryFactory parserFactoryFactory, List<String> java,
                      TimeoutLimits timeoutLimits) throws IOException, TikaException {
        this(tikaDir, parserFactoryFactory, null, java, timeoutLimits);
    }

    /**
     * @param tikaDir              directory containing jars from which to start
     *                             the child server and load the Parser
     * @param parserFactoryFactory factory to send to forked process to build parser
     *                             upon arrival
     * @param classLoader          class loader to use for non-parser resource
     *                             (content-handler, etc.)
     * @param java                 java commandline to use for the commandline server
     * @throws IOException
     * @throws TikaException
     */
    public ForkClient(Path tikaDir, ParserFactoryFactory parserFactoryFactory,
                      ClassLoader classLoader, List<String> java, TimeoutLimits timeoutLimits)
            throws IOException, TikaException {
        jar = null;
        loader = null;
        boolean ok = false;
        ProcessBuilder builder = new ProcessBuilder();
        List<String> command = new ArrayList<>(java);
        command.add("-cp");
        String dirString = tikaDir.toAbsolutePath().toString();
        if (!dirString.endsWith("/")) {
            dirString += "/*";
        } else {
            dirString += "/";
        }
        dirString = ProcessUtils.escapeCommandLine(dirString);
        command.add(dirString);
        command.add("org.apache.tika.fork.ForkServer");
        command.add(Long.toString(timeoutLimits.getPulseMS()));
        command.add(Long.toString(timeoutLimits.getParseTimeoutMS()));
        command.add(Long.toString(timeoutLimits.getWaitTimeoutMS()));
        builder.command(command);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        try {
            this.process = builder.start();

            this.output = new DataOutputStream(process.getOutputStream());
            this.input = new DataInputStream(process.getInputStream());

            waitForStartBeacon();
            if (classLoader != null) {
                output.writeByte(ForkServer.INIT_PARSER_FACTORY_FACTORY_LOADER);
            } else {
                output.writeByte(ForkServer.INIT_PARSER_FACTORY_FACTORY);
            }
            output.flush();
            sendObject(parserFactoryFactory, resources);
            if (classLoader != null) {
                sendObject(classLoader, resources);
            }
            waitForStartBeacon();
            ok = true;
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            if (!ok) {
                close();
            }
        }
    }


    public ForkClient(ClassLoader loader, Object object, List<String> java,
                      TimeoutLimits timeoutLimits) throws IOException, TikaException {
        boolean ok = false;
        try {
            this.loader = loader;
            this.jar = createBootstrapJar();

            ProcessBuilder builder = new ProcessBuilder();
            List<String> command = new ArrayList<>(java);
            command.add("-jar");
            command.add(jar.getPath());
            command.add(Long.toString(timeoutLimits.getPulseMS()));
            command.add(Long.toString(timeoutLimits.getParseTimeoutMS()));
            command.add(Long.toString(timeoutLimits.getWaitTimeoutMS()));
            builder.command(command);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            this.process = builder.start();

            this.output = new DataOutputStream(process.getOutputStream());
            this.input = new DataInputStream(process.getInputStream());

            waitForStartBeacon();
            output.writeByte(ForkServer.INIT_LOADER_PARSER);
            output.flush();
            sendObject(loader, resources);
            sendObject(object, resources);
            waitForStartBeacon();

            ok = true;
        } finally {
            if (!ok) {
                close();
            }
        }
    }

    /**
     * Creates a temporary jar file that can be used to bootstrap the forked
     * server process. Remember to remove the file when no longer used.
     *
     * @return the created jar file
     * @throws IOException if the bootstrap archive could not be created
     */
    private static File createBootstrapJar() throws IOException {
        File file = Files.createTempFile("apache-tika-fork-", ".jar").toFile();
        boolean ok = false;
        try {
            fillBootstrapJar(file);
            ok = true;
        } finally {
            if (!ok) {
                file.delete();
            }
        }
        return file;
    }

    /**
     * Fills in the jar file used to bootstrap the forked server process.
     * All the required <code>.class</code> files and a manifest with a
     * <code>Main-Class</code> entry are written into the archive.
     *
     * @param file file to hold the bootstrap archive
     * @throws IOException if the bootstrap archive could not be created
     */
    private static void fillBootstrapJar(File file) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(file))) {
            String manifest = "Main-Class: " + ForkServer.class.getName() + "\n";
            jar.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            jar.write(manifest.getBytes(UTF_8));

            Class<?>[] bootstrap = {ForkServer.class, ForkObjectInputStream.class, ForkProxy.class,
                    ClassLoaderProxy.class, MemoryURLConnection.class, MemoryURLStreamHandler.class,
                    MemoryURLStreamHandlerFactory.class, MemoryURLStreamRecord.class,
                    TikaException.class};
            ClassLoader loader = ForkServer.class.getClassLoader();
            for (Class<?> klass : bootstrap) {
                String path = klass.getName().replace('.', '/') + ".class";
                try (InputStream input = loader.getResourceAsStream(path)) {
                    jar.putNextEntry(new JarEntry(path));
                    IOUtils.copy(input, jar);
                }
            }
        }
    }

    private void waitForStartBeacon() throws IOException {
        while (true) {
            int type = input.read();
            if ((byte) type == ForkServer.READY) {
                return;
            } else if ((byte) type == ForkServer.FAILED_TO_START) {
                throw new IOException("Server had a catastrophic initialization failure");
            } else if (type == -1) {
                throw new IOException("EOF while waiting for start beacon");
            } else {
                //can't do this because of
                // ForkParserIntegrationTest
                // #testAttachingADebuggerOnTheForkedParserShouldWork
//                throw new IOException("Unexpected byte while waiting for start beacon: "+type);
            }
        }
    }

    public synchronized boolean ping() {
        try {
            output.writeByte(ForkServer.PING);
            output.flush();
            while (true) {
                int type = input.read();
                return type == ForkServer.PING;
            }
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized Throwable call(String method, Object... args)
            throws IOException, TikaException {
        filesProcessed++;
        List<ForkResource> r = new ArrayList<>(resources);
        output.writeByte(ForkServer.CALL);
        output.writeUTF(method);
        for (Object arg : args) {
            sendObject(arg, r);
        }
        return waitForResponse(r);
    }

    public int getFilesProcessed() {
        return filesProcessed;
    }

    /**
     * Serializes the object first into an in-memory buffer and then
     * writes it to the output stream with a preceding size integer.
     *
     * @param object    object to be serialized
     * @param resources list of fork resources, used when adding proxies
     * @throws IOException if the object could not be serialized
     */
    private void sendObject(Object object, List<ForkResource> resources)
            throws IOException, TikaException {
        int n = resources.size();
        if (object instanceof InputStream) {
            resources.add(new InputStreamResource((InputStream) object));
            object = new InputStreamProxy(n);
        } else if (object instanceof RecursiveParserWrapperHandler) {
            resources.add(new RecursiveMetadataContentHandlerResource(
                    (RecursiveParserWrapperHandler) object));
            object = new RecursiveMetadataContentHandlerProxy(n,
                    ((RecursiveParserWrapperHandler) object).getContentHandlerFactory());
        } else if (object instanceof ContentHandler &&
                !(object instanceof AbstractRecursiveParserWrapperHandler)) {
            resources.add(new ContentHandlerResource((ContentHandler) object));
            object = new ContentHandlerProxy(n);
        } else if (object instanceof ClassLoader) {
            resources.add(new ClassLoaderResource((ClassLoader) object));
            object = new ClassLoaderProxy(n);
        }

        try {
            ForkObjectInputStream.sendObject(object, output);
        } catch (NotSerializableException nse) {
            // Build a more friendly error message for this
            throw new TikaException("Unable to serialize " + object.getClass().getSimpleName() +
                    " to pass to the Forked Parser", nse);
        }

        waitForResponse(resources);
    }

    public synchronized void close() {
        try {
            if (output != null) {
                output.close();
            }
            if (input != null) {
                input.close();
            }
        } catch (IOException ignore) {
        }
        if (process != null) {
            process.destroyForcibly();
            try {
                //TIKA-1933
                process.waitFor();
            } catch (InterruptedException e) {
                //swallow
            }
        }
        if (jar != null) {
            jar.delete();
        }
    }

    private Throwable waitForResponse(List<ForkResource> resources) throws IOException {
        output.flush();
        while (!Thread.currentThread().isInterrupted()) {
            int type = input.read();
            if (type == -1) {
                throw new IOException("Lost connection to a forked server process");
            } else if (type == ForkServer.RESOURCE) {
                ForkResource resource = resources.get(input.readUnsignedByte());
                resource.process(input, output);
            } else if ((byte) type == ForkServer.ERROR) {
                try {
                    return (Throwable) ForkObjectInputStream.readObject(input, loader);
                } catch (ClassNotFoundException e) {
                    throw new IOException("Unable to deserialize an exception", e);
                }
            } else {
                return null;
            }
        }
        throw new IOException(new InterruptedException());
    }

    public int getId() {
        return id;
    }
}
