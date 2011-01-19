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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.tika.io.IOUtils;
import org.xml.sax.ContentHandler;

class ForkClient {

    private final String java = "java"; // TODO: Make configurable

    private final String mx = "-Xmx32m"; // TODO: Make configurable

    private final List<ForkResource> resources = new ArrayList<ForkResource>();

    private final File jar;

    private final Process process;

    private final DataOutputStream output;

    private final DataInputStream input;

    private final InputStream error;

    public ForkClient(ClassLoader loader, Object object) throws IOException {
        boolean ok = false;
        try {
            this.jar = createBootstrapJar();

            ProcessBuilder builder = new ProcessBuilder();
            builder.command(java, mx, "-jar", jar.getPath());
            this.process = builder.start();

            this.output = new DataOutputStream(process.getOutputStream());
            this.input = new DataInputStream(process.getInputStream());
            this.error = process.getErrorStream();

            sendObject(loader, resources);
            sendObject(object, resources);

            ok = true;
        } finally {
            if (!ok) {
                close();
            }
        }
    }

    public synchronized void call(String method, Object... args)
            throws IOException {
        List<ForkResource> r = new ArrayList<ForkResource>(resources);
        output.writeByte(ForkServer.CALL);
        output.writeUTF(method);
        for (int i = 0; i < args.length; i++) {
            sendObject(args[i], r);
        }
        waitForResponse(r);
    }

    /**
     * Serializes the object first into an in-memory buffer and then
     * writes it to the output stream with a preceding size integer.
     *
     * @param object object to be serialized
     * @param resources list of fork resources, used when adding proxies
     * @throws IOException if the object could not be serialized
     */
    private void sendObject(Object object, List<ForkResource> resources)
            throws IOException {
        int n = resources.size();
        if (object instanceof InputStream) {
            resources.add(new InputStreamResource((InputStream) object));
            object = new InputStreamProxy(n);
        } else if (object instanceof ContentHandler) {
            resources.add(new ContentHandlerResource((ContentHandler) object));
            object = new ContentHandlerProxy(n);
        } else if (object instanceof ClassLoader) {
            resources.add(new ClassLoaderResource((ClassLoader) object));
            object = new ClassLoaderProxy(n);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutputStream serializer = new ObjectOutputStream(buffer);
        serializer.writeObject(object);
        serializer.close();

        byte[] data = buffer.toByteArray();
        output.writeInt(data.length);
        output.write(data);

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
            if (error != null) {
                error.close();
            }
        } catch (IOException ignore) {
        }
        if (process != null) {
            process.destroy();
        }
        if (jar != null) {
            jar.delete();
        }
    }

    private byte waitForResponse(List<ForkResource> resources)
            throws IOException {
        output.flush();
        while (true) {
            consumeErrorStream();
            int type = input.read();
            if (type == -1) {
                throw new IOException(
                        "Lost connection to a forked server process");
            } else if (type == ForkServer.RESOURCE) {
                ForkResource resource =
                    resources.get(input.readUnsignedByte());
                resource.process(input, output);
            } else {
                return (byte) type;
            }
        }
    }

    /**
     * Consumes all pending bytes from the standard error stream of the
     * forked server process, and prints them out to the standard error
     * stream of this process. This method should be called always before
     * expecting some output from the server, to prevent the server from
     * blocking due to a filled up pipe buffer of the error stream.
     *
     * @throws IOException if the error stream could not be read
     */
    private void consumeErrorStream() throws IOException {
        int n;
        while ((n = error.available()) > 0) {
            byte[] b = new byte[n];
            n = error.read(b);
            if (n > 0) {
                System.err.write(b, 0, n);
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
        File file = File.createTempFile("apache-tika-fork-", ".jar");
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
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(file));
        try {
            String manifest =
                "Main-Class: " + ForkServer.class.getName() + "\n";
            jar.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            jar.write(manifest.getBytes("UTF-8"));

            Class<?>[] bootstrap = {
                    ForkServer.class, ForkSerializer.class,
                    ForkProxy.class, ClassLoaderProxy.class,
                    MemoryURLConnection.class, MemoryURLStreamHandler.class,
                    MemoryURLStreamHandlerFactory.class
            };
            ClassLoader loader = ForkServer.class.getClassLoader();
            for (Class<?> klass : bootstrap) {
                String path = klass.getName().replace('.', '/') + ".class";
                InputStream input = loader.getResourceAsStream(path);
                try {
                    jar.putNextEntry(new JarEntry(path));
                    IOUtils.copy(input, jar);
                } finally {
                    input.close();
                }
            }
        } finally {
            jar.close();
        }
    }

}
